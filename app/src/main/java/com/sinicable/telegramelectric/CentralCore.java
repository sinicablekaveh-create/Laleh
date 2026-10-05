package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CentralCore {
    public enum RunState { STOPPED, RUNNING, ERROR }

    public enum ScheduleMode {
        EVERY_5_MINUTES,
        EVERY_10_MINUTES,
        EVERY_15_MINUTES,
        EVERY_30_MINUTES,
        EVERY_HOUR,
        THREE_PER_HOUR,
        FIVE_PER_HOUR,
        TEN_PER_DAY
    }

    public interface Listener {
        void onStatus(String message);
        void onDataChanged();
    }

    private static final String PREFS = "central_core";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_PHOTO_PATH = "photo_path";
    private static final String KEY_MODE = "mode";
    private static final String KEY_GROUPS = "selected_groups";
    private static final String KEY_SENT_COUNT = "sent_count";

    private static final long SEARCH_STEP_MS = 30_000L;
    private static final Pattern FLOOD_WAIT = Pattern.compile("FLOOD_WAIT[_ ]?(\\d+)", Pattern.CASE_INSENSITIVE);

    private final TelegramClientManager telegram;
    // Only the worker initializes and uses the search queue. UI reads a snapshot.
    private SmartSearchQueue smartQueue;
    private final Runnable initializeQueue;
    private volatile int cachedQueueSize;
    private volatile Listener listener;
    private final SharedPreferences prefs;
    private final HandlerThread workerThread;
    private final Handler handler;
    private boolean closed;
    private RunState runState = RunState.STOPPED;
    private String statusMessage = "هسته مرکزی متوقف است.";
    private static final Listener NO_LISTENER = new Listener() {
        @Override public void onStatus(String message) { }
        @Override public void onDataChanged() { }
    };

    private final Set<Long> selectedGroups = new HashSet<>();

    private boolean enabled;
    private boolean searchInFlight;
    private boolean sendInFlight;
    private long runGeneration;
    private String message;
    private String photoPath;
    private long photoRevision;
    private ScheduleMode mode;
    private long sentCount;
    private long windowEndsAt;
    private int targetCursor;

    private Runnable sendTick;
    private Runnable discoveryTick;

    public CentralCore(
            Context context,
            TelegramClientManager telegram,
            WordBank wordBank,
            Listener listener
    ) {
        this.telegram = telegram;
        this.listener = listener == null ? NO_LISTENER : listener;
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Context application = context.getApplicationContext();
        workerThread = new HandlerThread("telegram-central-core");
        workerThread.start();
        handler = new Handler(workerThread.getLooper());
        cachedQueueSize = wordBank.size();
        initializeQueue = () -> {
            if (smartQueue == null) {
                smartQueue = new SmartSearchQueue(application, wordBank);
                cachedQueueSize = smartQueue.size();
            }
        };


        enabled = prefs.getBoolean(KEY_ENABLED, false);
        message = prefs.getString(KEY_MESSAGE, "");
        photoPath = prefs.getString(KEY_PHOTO_PATH, "");
        sentCount = prefs.getLong(KEY_SENT_COUNT, 0L);

        String savedMode = prefs.getString(KEY_MODE, ScheduleMode.EVERY_5_MINUTES.name());
        try {
            mode = ScheduleMode.valueOf(savedMode);
        } catch (Throwable ignored) {
            mode = ScheduleMode.EVERY_5_MINUTES;
        }

        selectedGroups.addAll(parseIds(prefs.getStringSet(KEY_GROUPS, new HashSet<>())));

        handler.post(() -> {
            final long generation;
            synchronized (this) {
                if (closed) return;
                generation = runGeneration;
            }
            try {
                initializeQueue.run();
                synchronized (this) { if (!closed) this.listener.onDataChanged(); }
            } catch (RuntimeException error) {
                fail(generation, error);
            }
        });

        if (enabled) {
            runState = RunState.RUNNING;
            statusMessage = "هسته مرکزی: در حال بازیابی اجرا...";
            scheduleSend(runGeneration, 5_000L);
        }
    }

    public synchronized void setListener(Listener listener) {
        this.listener = listener == null ? NO_LISTENER : listener;
        this.listener.onStatus(statusMessage);
        this.listener.onDataChanged();
    }

    public synchronized RunState getRunState() { return runState; }

    public synchronized String getStatusMessage() { return statusMessage; }

    private synchronized void reportStatus(String value) {
        statusMessage = value;
        listener.onStatus(value);
    }

    private synchronized void reportStatus(long generation, String value) {
        if (isCurrentRun(generation)) reportStatus(value);
    }

    public synchronized void setMessage(String value) {
        message = value == null ? "" : value.trim();
        prefs.edit().putString(KEY_MESSAGE, message).apply();
    }

    public synchronized String getMessage() {
        return message == null ? "" : message;
    }

    public synchronized void setPhotoPath(String value) {
        photoRevision++;
        photoPath = value == null ? "" : value.trim();
        prefs.edit().putString(KEY_PHOTO_PATH, photoPath).apply();
        listener.onDataChanged();
    }

    public synchronized String getPhotoPath() {
        return photoPath == null ? "" : photoPath;
    }

    public synchronized long getPhotoRevision() {
        return photoRevision;
    }

    public synchronized boolean hasPhoto() {
        return PhotoMessageStore.exists(photoPath);
    }

    public synchronized void clearPhoto(Context context) {
        photoRevision++;
        String old = photoPath;
        photoPath = "";
        prefs.edit().remove(KEY_PHOTO_PATH).apply();
        PhotoMessageStore.clear(context, null);
        listener.onDataChanged();
    }

    public synchronized void setMode(ScheduleMode value) {
        ScheduleMode next = value == null ? ScheduleMode.EVERY_5_MINUTES : value;
        if (mode == next) return;
        mode = next;
        prefs.edit().putString(KEY_MODE, mode.name()).apply();

    }

    public synchronized ScheduleMode getMode() {
        return mode;
    }

    public synchronized void setGroupSelected(long id, boolean selected) {
        if (selectedGroups.contains(id) == selected) return;
        if (selected) {
            selectedGroups.add(id);
        } else {
            selectedGroups.remove(id);
        }
        persistIds();
        listener.onDataChanged();
    }

    public synchronized boolean isGroupSelected(long id) {
        return selectedGroups.contains(id);
    }

    public synchronized int selectedGroupCount() {
        int count = 0;
        boolean withPhoto = hasPhoto();
        for (Long id : selectedGroups) {
            TelegramClientManager.GroupInfo info = telegram.getTargetGroup(id);
            if (allowsSelectedContent(info, withPhoto)) {
                count++;
            }
        }
        return count;
    }

    public synchronized long getSentCount() {
        return sentCount;
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized int queueSize() {
        return cachedQueueSize;
    }

    public synchronized boolean start() {
        if (closed) return false;
        if (enabled) {
            reportStatus("تنظیمات ثبت شد؛ ارسال در زمان بعدی مجاز انجام می‌شود.");
            listener.onDataChanged();
            return true;
        }
        runGeneration++;
        searchInFlight = false;
        sendInFlight = false;
        enabled = true;
        runState = RunState.RUNNING;
        prefs.edit().putBoolean(KEY_ENABLED, true).apply();
        cancelTicks();
        reportStatus("هسته مرکزی فعال شد؛ جستجو شروع می‌شود و ارسال فقط با متن و گروه هدف مجاز انجام می‌شود.");
        scheduleSend(runGeneration, 0L);
        listener.onDataChanged();
        return true;
    }

    public synchronized void stop() {
        if (closed) return;
        enabled = false;
        runState = RunState.STOPPED;
        runGeneration++;
        searchInFlight = false;
        sendInFlight = false;
        windowEndsAt = 0L;
        prefs.edit().putBoolean(KEY_ENABLED, false).apply();
        cancelTicks();
        reportStatus("هسته مرکزی متوقف شد.");
        listener.onDataChanged();
    }

    public synchronized void shutdown() {
        if (closed) return;
        closed = true;
        enabled = false;
        runState = RunState.STOPPED;
        statusMessage = "هسته مرکزی متوقف شد.";
        runGeneration++;
        cancelTicks();
        searchInFlight = false;
        sendInFlight = false;
        listener = NO_LISTENER;
        // Keep the saved enabled preference for Activity/process recreation.
        workerThread.quitSafely();
    }

    private boolean isCurrentRun(long generation) {
        return enabled && !closed && generation == runGeneration;
    }

    private Runnable guarded(long generation, Runnable action) {
        return () -> {
            synchronized (this) { if (!isCurrentRun(generation)) return; }
            try {
                initializeQueue.run();
                synchronized (this) { if (!isCurrentRun(generation)) return; }
                action.run();
            } catch (RuntimeException error) {
                fail(generation, error);
            }
        };
    }

    private synchronized void cancelTicks() {
        if (sendTick != null) handler.removeCallbacks(sendTick);
        if (discoveryTick != null) handler.removeCallbacks(discoveryTick);
    }

    private synchronized void scheduleSend(long generation, long delay) {
        if (!isCurrentRun(generation)) return;
        if (sendTick != null) handler.removeCallbacks(sendTick);
        sendTick = guarded(generation, () -> runSendCycle(generation));
        if (delay == 0L) handler.post(sendTick);
        else handler.postDelayed(sendTick, delay);
    }

    private synchronized void scheduleDiscovery(long generation, long delay) {
        if (!isCurrentRun(generation)) return;
        if (discoveryTick != null) handler.removeCallbacks(discoveryTick);
        discoveryTick = guarded(generation, () -> runDiscoveryStep(generation));
        if (delay == 0L) handler.post(discoveryTick);
        else handler.postDelayed(discoveryTick, delay);
    }

    private synchronized void postResult(long generation, Runnable action) {
        if (isCurrentRun(generation)) handler.post(guarded(generation, action));
    }

    private synchronized void fail(long generation, RuntimeException error) {
        if (closed || generation != runGeneration) return;
        enabled = false;
        runState = RunState.ERROR;
        runGeneration++;
        searchInFlight = false;
        sendInFlight = false;
        cancelTicks();
        prefs.edit().putBoolean(KEY_ENABLED, false).apply();
        reportStatus("خطای هسته مرکزی: " + error.getClass().getSimpleName()
                + ". برای تلاش دوباره START را بزنید.");
        listener.onDataChanged();
    }

    private void runSendCycle(long generation) {
        synchronized (this) {
            if (!isCurrentRun(generation) || sendInFlight) return;
        }

        if (!telegram.isReadyForSending()) {
            reportStatus(generation, "هسته مرکزی: منتظر اتصال کامل تلگرام...");
            scheduleSend(generation, 60_000L);
            return;
        }

        if (getMessage().trim().isEmpty()) {
            reportStatus(generation, "جستجوی هسته فعال است؛ برای ارسال، متن پیام را وارد و تنظیمات را ثبت کنید.");
            beginDiscoveryWindow(generation, 60_000L);
            return;
        }

        final List<TelegramClientManager.GroupInfo> targets = eligibleTargets();
        if (targets.isEmpty()) {
            reportStatus(generation, "جستجوی هسته فعال است؛ برای ارسال، از «انتخاب گروه هدف» یک گروه دارای اجازهٔ ارسال انتخاب کنید.");
            beginDiscoveryWindow(generation, 60_000L);
            return;
        }

        final TelegramClientManager.GroupInfo target;
        synchronized (this) {
            if (!isCurrentRun(generation)) return;
            if (targetCursor >= targets.size()) targetCursor = 0;
            target = targets.get(targetCursor++);
            sendInFlight = true;
        }

        reportStatus(generation,
                hasPhoto()
                        ? "در حال ارسال عکس و متن به «" + target.title + "» ..."
                        : "در حال ارسال خودکار به «" + target.title + "» ..."
        );

        TelegramClientManager.SendCallback sendCallback = (success, resultMessage) -> postResult(generation, () -> {
            synchronized (CentralCore.this) {
                if (!isCurrentRun(generation)) return;
                sendInFlight = false;
            }

            if (success) {
                synchronized (CentralCore.this) {
                    if (!isCurrentRun(generation)) return;
                    sentCount++;
                    prefs.edit().putLong(KEY_SENT_COUNT, sentCount).apply();
                }
                reportStatus(generation,
                        "ارسال موفق به «" + target.title + "». پنجره جستجو تا ارسال بعدی شروع شد."
                );
                beginDiscoveryWindow(generation, intervalMillis());
            } else {
                long floodWait = parseFloodWaitMillis(resultMessage);
                if (floodWait > 0L) {
                    reportStatus(generation, "تلگرام محدودیت موقت اعمال کرده؛ ارسال بعدی پس از زمان مجاز انجام می‌شود.");
                    scheduleSend(generation, floodWait);
                } else {
                    reportStatus(generation, "ارسال ناموفق به «" + target.title + "»: " + resultMessage);
                    beginDiscoveryWindow(generation, intervalMillis());
                }
            }
            listener.onDataChanged();
        });

        synchronized (this) {
            if (!isCurrentRun(generation)) return;
            if (!selectedGroups.contains(target.id)) {
                beginDiscoveryWindow(generation, 60_000L);
                sendInFlight = false;
                return;
            }
            if (hasPhoto()) {
                telegram.sendPhotoToChat(
                        target.id,
                        getPhotoPath(),
                        getMessage(),
                        sendCallback
                );
            } else {
                telegram.sendTextToChat(target.id, getMessage(), sendCallback);
            }
        }
    }

    private synchronized void beginDiscoveryWindow(long generation, long duration) {
        if (!isCurrentRun(generation)) return;

        cancelTicks();

        windowEndsAt = SystemClock.elapsedRealtime() + duration;
        scheduleDiscovery(generation, 0L);
        scheduleSend(generation, duration);
    }

    private void runDiscoveryStep(long generation) {
        long remaining;
        synchronized (this) {
            if (!isCurrentRun(generation)) return;
            remaining = windowEndsAt - SystemClock.elapsedRealtime();
            if (remaining <= 10_000L) return;
            if (!telegram.isReadyForSending()) {
                scheduleDiscovery(generation, SEARCH_STEP_MS);
                return;
            }
            if (searchInFlight) {
                scheduleDiscovery(generation, 5_000L);
                return;
            }
            searchInFlight = true;
        }

        String query = smartQueue.nextQuery();
        cachedQueueSize = smartQueue.size();
        if (query == null || query.trim().isEmpty()) {
            synchronized (this) {
                if (!isCurrentRun(generation)) return;
                searchInFlight = false;
            }
            scheduleDiscovery(generation, SEARCH_STEP_MS);
            return;
        }

        int score = smartQueue.scoreFor(query);
        reportStatus(generation, "هسته مرکزی: جستجوی گروه‌های عمومی با «" + query + "» — امتیاز " + score);

        synchronized (this) {
            if (!isCurrentRun(generation)) return;
            telegram.discoverPublicGroupsForReview(query, (success, newItems, totalItems, resultMessage) -> postResult(generation, () -> {
                synchronized (CentralCore.this) {
                    if (!isCurrentRun(generation)) return;
                    searchInFlight = false;
                }

                if (success) {
                    smartQueue.recordResult(query, newItems, totalItems);
                    reportStatus(generation,
                            "جستجو «" + query + "»: " + totalItems + " گروه مرتبط، "
                                    + newItems + " مورد جدید. صف دوباره امتیازدهی شد."
                    );
                } else {
                    smartQueue.recordResult(query, 0, 0);
                    reportStatus(generation, "جستجوی «" + query + "» انجام نشد: " + resultMessage);
                }

                listener.onDataChanged();

                long left;
                synchronized (CentralCore.this) {
                    left = windowEndsAt - SystemClock.elapsedRealtime();
                }
                if (isEnabled() && left > 10_000L) {
                    scheduleDiscovery(generation, Math.min(SEARCH_STEP_MS, Math.max(5_000L, left - 10_000L)));
                }
            }));
        }
    }

    private List<TelegramClientManager.GroupInfo> eligibleTargets() {
        final Set<Long> selections;
        final boolean withPhoto;
        synchronized (this) {
            selections = new HashSet<>(selectedGroups);
            withPhoto = hasPhoto();
        }
        List<TelegramClientManager.GroupInfo> result = new ArrayList<>();
        for (TelegramClientManager.GroupInfo info : telegram.getTargetGroups()) {
            if (selections.contains(info.id) && allowsSelectedContent(info, withPhoto)) {
                result.add(info);
            }
        }
        return result;
    }

    private boolean allowsSelectedContent(TelegramClientManager.GroupInfo info, boolean withPhoto) {
        return info != null && (withPhoto ? info.canSendPhotos : info.canSend);
    }

    private synchronized long intervalMillis() {
        switch (mode) {
            case EVERY_10_MINUTES:
                return 10L * 60L * 1000L;
            case EVERY_15_MINUTES:
                return 15L * 60L * 1000L;
            case EVERY_30_MINUTES:
                return 30L * 60L * 1000L;
            case EVERY_HOUR:
                return 60L * 60L * 1000L;
            case THREE_PER_HOUR:
                return 20L * 60L * 1000L;
            case FIVE_PER_HOUR:
                return 12L * 60L * 1000L;
            case TEN_PER_DAY:
                return 144L * 60L * 1000L;
            case EVERY_5_MINUTES:
            default:
                return 5L * 60L * 1000L;
        }
    }

    private static long parseFloodWaitMillis(String message) {
        if (message == null) return 0L;
        Matcher matcher = FLOOD_WAIT.matcher(message.toUpperCase(Locale.ROOT));
        if (!matcher.find()) return 0L;

        try {
            long seconds = Long.parseLong(matcher.group(1));
            return Math.max(60_000L, seconds * 1000L);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private synchronized void persistIds() {
        Set<String> values = new HashSet<>();
        for (Long id : selectedGroups) {
            values.add(String.valueOf(id));
        }
        prefs.edit().putStringSet(KEY_GROUPS, values).apply();
    }

    private static Set<Long> parseIds(Set<String> values) {
        Set<Long> result = new HashSet<>();
        if (values == null) return result;

        for (String value : values) {
            try {
                result.add(Long.parseLong(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }
}
