package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CentralCore {
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
    private static final String KEY_RETRY_NOT_BEFORE = "retry_not_before";

    private static final long SEARCH_STEP_MS = 30_000L;
    private static final Pattern TELEGRAM_WAIT = Pattern.compile(
            "(?:FLOOD(?:_PREMIUM)?|SLOWMODE)_WAIT[_ ]?(\\d+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RETRY_AFTER = Pattern.compile(
            "RETRY\\s+AFTER\\s+(\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    private final TelegramClientManager telegram;
    private final WordBank wordBank;
    private final SmartSearchQueue smartQueue;
    private volatile Listener listener;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

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
    private long retryNotBefore;
    private int targetCursor;

    private final Runnable sendTick = new Runnable() {
        @Override
        public void run() {
            runSendCycle();
        }
    };

    private final Runnable discoveryTick = new Runnable() {
        @Override
        public void run() {
            runDiscoveryStep();
        }
    };

    public CentralCore(
            Context context,
            TelegramClientManager telegram,
            WordBank wordBank,
            Listener listener
    ) {
        this.telegram = telegram;
        this.wordBank = wordBank;
        this.listener = listener;
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.smartQueue = new SmartSearchQueue(context, wordBank);

        enabled = prefs.getBoolean(KEY_ENABLED, false);
        message = prefs.getString(KEY_MESSAGE, "");
        photoPath = prefs.getString(KEY_PHOTO_PATH, "");
        sentCount = prefs.getLong(KEY_SENT_COUNT, 0L);
        retryNotBefore = prefs.getLong(KEY_RETRY_NOT_BEFORE, 0L);

        String savedMode = prefs.getString(KEY_MODE, ScheduleMode.EVERY_5_MINUTES.name());
        try {
            mode = ScheduleMode.valueOf(savedMode);
        } catch (Throwable ignored) {
            mode = ScheduleMode.EVERY_5_MINUTES;
        }

        selectedGroups.addAll(parseIds(prefs.getStringSet(KEY_GROUPS, new HashSet<>())));

        if (enabled) {
            handler.postDelayed(sendTick, 5_000L);
        }
    }

    public void setListener(Listener listener) {
        if (listener != null) {
            this.listener = listener;
        }
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
        return smartQueue.size();
    }

    public synchronized boolean start() {
        if (enabled) {
            listener.onStatus("تنظیمات ثبت شد؛ ارسال در زمان بعدی مجاز انجام می‌شود.");
            listener.onDataChanged();
            return true;
        }
        runGeneration++;
        searchInFlight = false;
        sendInFlight = false;
        enabled = true;
        prefs.edit().putBoolean(KEY_ENABLED, true).apply();
        handler.removeCallbacks(sendTick);
        handler.removeCallbacks(discoveryTick);
        listener.onStatus("هسته مرکزی فعال شد؛ جستجو شروع می‌شود و ارسال فقط با متن و گروه هدف مجاز انجام می‌شود.");
        if (!sendInFlight) handler.post(sendTick);
        listener.onDataChanged();
        return true;
    }

    public synchronized void stop() {
        enabled = false;
        runGeneration++;
        searchInFlight = false;
        sendInFlight = false;
        windowEndsAt = 0L;
        prefs.edit().putBoolean(KEY_ENABLED, false).apply();
        handler.removeCallbacks(sendTick);
        handler.removeCallbacks(discoveryTick);
        listener.onStatus("هسته مرکزی متوقف شد.");
        listener.onDataChanged();
    }

    public synchronized void shutdown() {
        runGeneration++;
        handler.removeCallbacks(sendTick);
        handler.removeCallbacks(discoveryTick);
        searchInFlight = false;
        sendInFlight = false;
    }

    private void runSendCycle() {
        final long generation;
        final long retryDelay;
        synchronized (this) {
            if (!enabled || sendInFlight) return;
            generation = runGeneration;
            retryDelay = retryNotBefore - System.currentTimeMillis();
            if (retryDelay <= 0L && retryNotBefore != 0L) {
                retryNotBefore = 0L;
                prefs.edit().remove(KEY_RETRY_NOT_BEFORE).apply();
            }
        }

        if (retryDelay > 0L) {
            listener.onStatus("تلگرام محدودیت موقت اعمال کرده؛ تا پایان زمان مجاز در انتظار می‌مانیم.");
            handler.removeCallbacks(sendTick);
            handler.postDelayed(sendTick, retryDelay);
            return;
        }

        if (!telegram.isReadyForSending()) {
            listener.onStatus("هسته مرکزی: منتظر اتصال کامل تلگرام...");
            handler.postDelayed(sendTick, 60_000L);
            return;
        }

        if (getMessage().trim().isEmpty()) {
            listener.onStatus("جستجوی هسته فعال است؛ برای ارسال، متن پیام را وارد و تنظیمات را ثبت کنید.");
            beginDiscoveryWindow(60_000L);
            return;
        }

        final List<TelegramClientManager.GroupInfo> targets = eligibleTargets();
        if (targets.isEmpty()) {
            listener.onStatus("جستجوی هسته فعال است؛ برای ارسال، از «انتخاب گروه هدف» یک گروه دارای اجازهٔ ارسال انتخاب کنید.");
            beginDiscoveryWindow(60_000L);
            return;
        }

        final TelegramClientManager.GroupInfo target;
        synchronized (this) {
            if (targetCursor >= targets.size()) targetCursor = 0;
            target = targets.get(targetCursor++);
            sendInFlight = true;
        }

        listener.onStatus(
                hasPhoto()
                        ? "در حال ارسال عکس و متن به «" + target.title + "» ..."
                        : "در حال ارسال خودکار به «" + target.title + "» ..."
        );

        TelegramClientManager.SendCallback sendCallback = (success, resultMessage) -> handler.post(() -> {
            synchronized (CentralCore.this) {
                if (!enabled || generation != runGeneration) return;
                sendInFlight = false;
            }

            if (success) {
                synchronized (CentralCore.this) {
                    sentCount++;
                    prefs.edit().putLong(KEY_SENT_COUNT, sentCount).apply();
                }
                listener.onStatus(
                        "ارسال موفق به «" + target.title + "». پنجره جستجو تا ارسال بعدی شروع شد."
                );
                beginDiscoveryWindow(intervalMillis());
            } else {
                long retryWait = parseRetryWaitMillis(resultMessage);
                if (retryWait > 0L) {
                    listener.onStatus("تلگرام محدودیت موقت اعمال کرده؛ ارسال بعدی پس از زمان مجاز انجام می‌شود.");
                    rememberRetryDeadline(retryWait);
                    handler.postDelayed(sendTick, retryWait);
                } else {
                    listener.onStatus("ارسال ناموفق به «" + target.title + "»: " + resultMessage);
                    beginDiscoveryWindow(intervalMillis());
                }
            }
            listener.onDataChanged();
        });

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

    private synchronized void beginDiscoveryWindow(long duration) {
        if (!enabled) return;

        handler.removeCallbacks(sendTick);
        handler.removeCallbacks(discoveryTick);

        windowEndsAt = System.currentTimeMillis() + duration;
        handler.post(discoveryTick);
        handler.postDelayed(sendTick, duration);
    }

    private void runDiscoveryStep() {
        if (!isEnabled()) return;

        long remaining;
        final long generation;
        synchronized (this) {
            generation = runGeneration;
            remaining = windowEndsAt - System.currentTimeMillis();
            if (remaining <= 10_000L) return;
            if (!telegram.isReadyForSending()) {
                handler.postDelayed(discoveryTick, SEARCH_STEP_MS);
                return;
            }
            if (searchInFlight) {
                handler.postDelayed(discoveryTick, 5_000L);
                return;
            }
            searchInFlight = true;
        }

        String query = smartQueue.nextQuery();
        if (query == null || query.trim().isEmpty()) {
            synchronized (this) {
                searchInFlight = false;
            }
            handler.postDelayed(discoveryTick, SEARCH_STEP_MS);
            return;
        }

        int score = smartQueue.scoreFor(query);
        listener.onStatus("هسته مرکزی: جستجوی گروه‌های عمومی با «" + query + "» — امتیاز " + score);

        telegram.discoverPublicGroupsForReview(query, (success, newItems, totalItems, resultMessage) -> handler.post(() -> {
            synchronized (CentralCore.this) {
                if (!enabled || generation != runGeneration) return;
                searchInFlight = false;
            }

            boolean rateLimited = false;
            if (success) {
                smartQueue.recordResult(query, newItems, totalItems);
                listener.onStatus(
                        "جستجو «" + query + "»: " + totalItems + " گروه مرتبط، "
                                + newItems + " مورد جدید. صف دوباره امتیازدهی شد."
                );
            } else {
                long retryWait = parseRetryWaitMillis(resultMessage);
                if (retryWait > 0L) {
                    rateLimited = true;
                    rememberRetryDeadline(retryWait);
                    listener.onStatus("جستجوی Telegram موقتاً محدود شد؛ تا پایان زمان مجاز درخواست دیگری ارسال نمی‌شود.");
                } else {
                    smartQueue.recordResult(query, 0, 0);
                    listener.onStatus("جستجوی «" + query + "» انجام نشد: " + resultMessage);
                }
            }

            listener.onDataChanged();

            long left;
            synchronized (CentralCore.this) {
                left = windowEndsAt - System.currentTimeMillis();
            }
            if (!rateLimited && isEnabled() && left > 10_000L) {
                handler.removeCallbacks(discoveryTick);
                handler.postDelayed(discoveryTick, Math.min(SEARCH_STEP_MS, Math.max(5_000L, left - 10_000L)));
            }
        }));
    }

    private synchronized List<TelegramClientManager.GroupInfo> eligibleTargets() {
        List<TelegramClientManager.GroupInfo> result = new ArrayList<>();
        boolean withPhoto = hasPhoto();
        for (TelegramClientManager.GroupInfo info : telegram.getTargetGroups()) {
            if (selectedGroups.contains(info.id) && allowsSelectedContent(info, withPhoto)) {
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

    static long parseRetryWaitMillis(String message) {
        if (message == null) return 0L;
        String normalized = message.toUpperCase(Locale.ROOT);
        Matcher matcher = TELEGRAM_WAIT.matcher(normalized);
        if (!matcher.find()) {
            matcher = RETRY_AFTER.matcher(normalized);
            if (!matcher.find()) return 0L;
        }

        try {
            long seconds = Long.parseLong(matcher.group(1));
            if (seconds <= 0L) return 0L;
            if (seconds >= Long.MAX_VALUE / 1000L) return Long.MAX_VALUE;
            return seconds * 1000L;
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static long safeDeadline(long now, long delay) {
        if (delay >= Long.MAX_VALUE - now) return Long.MAX_VALUE;
        return now + delay;
    }

    private synchronized void rememberRetryDeadline(long delay) {
        long deadline = safeDeadline(System.currentTimeMillis(), delay);
        retryNotBefore = Math.max(retryNotBefore, deadline);
        prefs.edit().putLong(KEY_RETRY_NOT_BEFORE, retryNotBefore).apply();
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
