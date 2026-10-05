package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CentralCore {
    public enum ScheduleMode {
        EVERY_5_MINUTES,
        FIVE_PER_HOUR,
        TEN_PER_DAY
    }

    public interface Listener {
        void onStatus(String message);
    }

    private static final String PREFS = "central_core";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_MODE = "mode";
    private static final String KEY_GROUPS = "selected_groups";
    private static final String KEY_CONTACTS = "selected_contacts";
    private static final String KEY_SENT_COUNT = "sent_count";

    private final TelegramClientManager telegram;
    private final Listener listener;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Set<Long> selectedGroups = new HashSet<>();
    private final Set<Long> selectedContacts = new HashSet<>();

    private boolean enabled;
    private String message;
    private ScheduleMode mode;
    private long sentCount;
    private int cursor = 0;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!enabled) return;

            if (!telegram.isReadyForSending()) {
                listener.onStatus("هسته مرکزی: منتظر اتصال کامل تلگرام...");
                scheduleNext(60_000L);
                return;
            }

            if (message == null || message.trim().isEmpty()) {
                listener.onStatus("هسته مرکزی: متن پیام خالی است.");
                scheduleNext(intervalMillis());
                return;
            }

            List<Target> targets = getTargets();
            if (targets.isEmpty()) {
                listener.onStatus("هسته مرکزی: هیچ گروه یا مخاطبی برای ارسال انتخاب نشده است.");
                scheduleNext(intervalMillis());
                return;
            }

            if (cursor >= targets.size()) cursor = 0;
            Target target = targets.get(cursor++);
            listener.onStatus("در حال ارسال خودکار به " + target.title + " ...");

            TelegramClientManager.SendCallback callback = (success, resultMessage) -> {
                if (success) {
                    sentCount++;
                    prefs.edit().putLong(KEY_SENT_COUNT, sentCount).apply();
                    listener.onStatus("ارسال موفق به " + target.title + " — مجموع ارسال: " + sentCount);
                } else {
                    listener.onStatus("ارسال ناموفق به " + target.title + ": " + resultMessage);
                }
                scheduleNext(intervalMillis());
            };

            if (target.kind == TargetKind.GROUP) {
                telegram.sendTextToChat(target.id, message, callback);
            } else {
                telegram.sendTextToUser(target.id, message, callback);
            }
        }
    };

    public CentralCore(Context context, TelegramClientManager telegram, Listener listener) {
        this.telegram = telegram;
        this.listener = listener;
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        enabled = prefs.getBoolean(KEY_ENABLED, false);
        message = prefs.getString(KEY_MESSAGE, "");
        sentCount = prefs.getLong(KEY_SENT_COUNT, 0);

        String savedMode = prefs.getString(KEY_MODE, ScheduleMode.EVERY_5_MINUTES.name());
        try {
            mode = ScheduleMode.valueOf(savedMode);
        } catch (Exception e) {
            mode = ScheduleMode.EVERY_5_MINUTES;
        }

        selectedGroups.addAll(parseIds(prefs.getStringSet(KEY_GROUPS, new HashSet<>())));
        selectedContacts.addAll(parseIds(prefs.getStringSet(KEY_CONTACTS, new HashSet<>())));

        if (enabled) {
            scheduleNext(5_000L);
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
            handler.removeCallbacks(tick);
            scheduleNext(1_000L);
        }
    }

    public synchronized ScheduleMode getMode() {
        return mode;
    }

    public synchronized void setGroupSelected(long id, boolean selected) {
        if (selected) selectedGroups.add(id); else selectedGroups.remove(id);
        persistIds(KEY_GROUPS, selectedGroups);
    }

    public synchronized void setContactSelected(long id, boolean selected) {
        if (selected) selectedContacts.add(id); else selectedContacts.remove(id);
        persistIds(KEY_CONTACTS, selectedContacts);
    }

    public synchronized boolean isGroupSelected(long id) {
        return selectedGroups.contains(id);
    }

    public synchronized boolean isContactSelected(long id) {
        return selectedContacts.contains(id);
    }

    public synchronized int selectedTargetCount() {
        return selectedGroups.size() + selectedContacts.size();
    }

    public synchronized long getSentCount() {
        return sentCount;
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized void start() {
        enabled = true;
        prefs.edit().putBoolean(KEY_ENABLED, true).apply();
        handler.removeCallbacks(tick);
        listener.onStatus("هسته مرکزی فعال شد.");
        scheduleNext(1_000L);
    }

    public synchronized void stop() {
        enabled = false;
        prefs.edit().putBoolean(KEY_ENABLED, false).apply();
        handler.removeCallbacks(tick);
        listener.onStatus("هسته مرکزی متوقف شد.");
    }

    public synchronized void sendOneNow() {
        if (!enabled) {
            enabled = true;
            handler.removeCallbacks(tick);
            handler.post(tick);
            enabled = false;
            return;
        }
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    private synchronized List<Target> getTargets() {
        List<Target> result = new ArrayList<>();

        for (TelegramClientManager.RecipientInfo info : telegram.getFoundGroups()) {
            if (selectedGroups.contains(info.id)) {
                result.add(new Target(TargetKind.GROUP, info.id, info.title));
            }
        }

        for (TelegramClientManager.RecipientInfo info : telegram.getTelegramContacts()) {
            if (selectedContacts.contains(info.id)) {
                result.add(new Target(TargetKind.CONTACT, info.id, info.title));
            }
        }

        return result;
    }

    private synchronized void scheduleNext(long delay) {
        if (!enabled) return;
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, Math.max(1_000L, delay));
    }

    private synchronized long intervalMillis() {
        switch (mode) {
            case FIVE_PER_HOUR:
                return 12L * 60L * 1000L;
            case TEN_PER_DAY:
                return 144L * 60L * 1000L;
            case EVERY_5_MINUTES:
            default:
                return 5L * 60L * 1000L;
        }
    }

    private void persistIds(String key, Set<Long> ids) {
        Set<String> values = new HashSet<>();
        for (Long id : ids) values.add(String.valueOf(id));
        prefs.edit().putStringSet(key, values).apply();
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

    private enum TargetKind { GROUP, CONTACT }

    private static final class Target {
        final TargetKind kind;
        final long id;
        final String title;

        Target(TargetKind kind, long id, String title) {
            this.kind = kind;
            this.id = id;
            this.title = title;
        }
    }
}
