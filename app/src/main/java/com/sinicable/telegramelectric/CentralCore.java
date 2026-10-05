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
    private static final String KEY_MODE = "mode";
    private static final String KEY_GROUPS = "selected_groups";
    private static final String KEY_SENT_COUNT = "sent_count";

    private static final long SEARCH_STEP_MS = 30_000L;
    private static final Pattern FLOOD_WAIT = Pattern.compile("FLOOD_WAIT[_ ]?(\\d+)", Pattern.CASE_INSENSITIVE);

    private final TelegramClientManager telegram;
    private final WordBank wordBank;
    private final SmartSearchQueue smartQueue;
    private final Listener listener;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Set<Long> selectedGroups = new HashSet<>();

    private boolean enabled;
    private boolean searchInFlight;
    private String message;
    private ScheduleMode mode;
    private long sentCount;
    private long windowEndsAt;
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
        sentCount = prefs.getLong(KEY_SENT_COUNT, 0L);

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

    public synchronized void setMessage(String value) {
        message = value == null ? "" : value.trim();
        prefs.edit().putString(KEY_MESSAGE, message).apply();
    }

    public synchronized String getMessage() {
        return message == null ? "" : message;
    }

    public synchronized void setMode(ScheduleMode value) {
        mode = value == null ? ScheduleMode.EVERY_5_MINUTES : value;
        prefs.edit().putString(KEY_MODE, mode.name()).apply();

        if (enabled) {
            handler.removeCallbacks(sendTick);
            handler.removeCallbacks(discoveryTick);
            handler.postDelayed(sendTick, 1_000L);
        }
    }

    public synchronized ScheduleMode getMode() {
        return mode;
    }

    public synchronized void setGroupSelected(long id, boolean selected) {
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
        return selectedGroups.size();
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
        if (message == null || message.trim().isEmpty()) {
            listener.onStatus("هسته مرکزی: متن پیام را وارد کنید.");
            return false;
        }

        if (selectedGroups.isEmpty()) {
            listener.onStatus("هسته مرکزی: حداقل یک گروه هدف انتخاب کنید.");
            return false;
        }

        enabled = true;
        prefs.edit().putBoolean(KEY_ENABLED, true).apply();
        handler.removeCallbacks(sendTick);
        handler.removeCallbacks(discoveryTick);
        listener.onStatus("هسته مرکزی فعال شد؛ ارسال فقط به گروه‌های هدف انتخاب‌شده انجام می‌شود.");
        handler.post(sendTick);
        listener.onDataChanged();
        return true;
    }

    public synchronized void stop() {
        enabled = false;
        searchInFlight = false;
        windowEndsAt = 0L;
        prefs.edit().putBoolean(KEY_ENABLED, false).apply();
        handler.removeCallbacks(sendTick);
        handler.removeCallbacks(discoveryTick);
        listener.onStatus("هسته مرکزی متوقف شد.");
        listener.onDataChanged();
    }

    public synchronized void shutdown() {
        handler.removeCallbacks(sendTick);
        handler.removeCallbacks(discoveryTick);
        searchInFlight = false;
    }

    private void runSendCycle() {
        if (!isEnabled()) return;

        if (!telegram.isReadyForSending()) {
            listener.onStatus("هسته مرکزی: منتظر اتصال کامل تلگرام...");
            handler.postDelayed(sendTick, 60_000L);
            return;
        }

        final List<TelegramClientManager.GroupInfo> targets = eligibleTargets();
        if (targets.isEmpty()) {
            listener.onStatus("هسته مرکزی: گروه هدف قابل ارسال پیدا نشد. انتخاب گروه‌ها را بررسی کنید.");
            handler.postDelayed(sendTick, 60_000L);
            return;
        }

        final TelegramClientManager.GroupInfo target;
        synchronized (this) {
            if (targetCursor >= targets.size()) targetCursor = 0;
            target = targets.get(targetCursor++);
        }

        listener.onStatus("در حال ارسال خودکار به «" + target.title + "» ...");
        telegram.sendTextToChat(target.id, getMessage(), (success, resultMessage) -> {
            if (!isEnabled()) return;

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
                long floodWait = parseFloodWaitMillis(resultMessage);
                if (floodWait > 0L) {
                    listener.onStatus("تلگرام محدودیت موقت اعمال کرده؛ ارسال بعدی پس از زمان مجاز انجام می‌شود.");
                    handler.postDelayed(sendTick, floodWait);
                } else {
                    listener.onStatus("ارسال ناموفق به «" + target.title + "»: " + resultMessage);
                    beginDiscoveryWindow(intervalMillis());
                }
            }
            listener.onDataChanged();
        });
    }

    private synchronized void beginDiscoveryWindow(long duration) {
        if (!enabled) return;

        handler.removeCallbacks(sendTick);
        handler.removeCallbacks(discoveryTick);

        windowEndsAt = System.currentTimeMillis() + duration;
        searchInFlight = false;

        handler.post(discoveryTick);
        handler.postDelayed(sendTick, duration);
    }

    private void runDiscoveryStep() {
        if (!isEnabled()) return;

        long remaining;
        synchronized (this) {
            remaining = windowEndsAt - System.currentTimeMillis();
            if (remaining <= 10_000L) return;
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
        listener.onStatus("هسته مرکزی: جستجوی گروه‌های موجود با «" + query + "» — امتیاز " + score);

        telegram.searchKnownGroups(query, (success, newItems, totalItems, resultMessage) -> {
            synchronized (CentralCore.this) {
                searchInFlight = false;
            }

            if (success) {
                smartQueue.recordResult(query, newItems, totalItems);
                listener.onStatus(
                        "جستجو «" + query + "»: " + totalItems + " گروه مرتبط، "
                                + newItems + " مورد جدید. صف دوباره امتیازدهی شد."
                );
            } else {
                smartQueue.recordResult(query, 0, 0);
                listener.onStatus("جستجوی «" + query + "» انجام نشد: " + resultMessage);
            }

            listener.onDataChanged();

            long left;
            synchronized (CentralCore.this) {
                left = windowEndsAt - System.currentTimeMillis();
            }
            if (isEnabled() && left > 10_000L) {
                handler.postDelayed(discoveryTick, Math.min(SEARCH_STEP_MS, Math.max(5_000L, left - 10_000L)));
            }
        });
    }

    private synchronized List<TelegramClientManager.GroupInfo> eligibleTargets() {
        List<TelegramClientManager.GroupInfo> result = new ArrayList<>();
        for (TelegramClientManager.GroupInfo info : telegram.getFoundGroups()) {
            if (selectedGroups.contains(info.id) && info.canSend) {
                result.add(info);
            }
        }
        return result;
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
