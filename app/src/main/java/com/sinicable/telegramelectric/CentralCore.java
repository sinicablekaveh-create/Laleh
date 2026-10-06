package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    private static final String KEY_WORD_SEARCH_ENABLED = "word_search_enabled";
    private static final String KEY_SEARCH_NOT_BEFORE = "search_not_before";

    private static final long SEARCH_STEP_MS = 30_000L;
    private static final long SEARCH_TIMEOUT_MS = 90_000L;
    private static final Pattern FLOOD_WAIT = Pattern.compile("FLOOD_WAIT[_ ]?(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RETRY_AFTER = Pattern.compile("retry\\s+after\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RATE_LIMIT = Pattern.compile("(?:Telegram\\s+429\\b|FLOOD_WAIT)", Pattern.CASE_INSENSITIVE);
    private static final Logger LOG = Logger.getLogger("WordSearch");

    private final TelegramClientManager telegram;
    private final WordBank wordBank;
    private final SmartSearchQueue smartQueue;
    private volatile Listener listener;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Set<Long> selectedGroups = new HashSet<>();

    private boolean enabled;
    private boolean searchInFlight;
    private boolean wordSearchRunning;
    private long searchGeneration;
    private long searchRequestToken;
    private long searchNotBefore;
    private long nextSearchAt;
    private String wordSearchStatus = "جستجوی بانک واژه آماده است.";
    private Runnable searchTimeout;
    private boolean sendInFlight;
    private long runGeneration;
    private String message;
    private String photoPath;
    private long photoRevision;
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
        wordSearchRunning = prefs.getBoolean(KEY_WORD_SEARCH_ENABLED, enabled);
        searchNotBefore = prefs.getLong(KEY_SEARCH_NOT_BEFORE, 0L);
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

        if (enabled) {
            handler.postDelayed(sendTick, 5_000L);
            if (wordSearchRunning) handler.postDelayed(discoveryTick, 6_000L);
        } else if (wordSearchRunning) {
            wordSearchStatus = "ادامهٔ جستجوی ذخیره‌شده؛ در انتظار اتصال تلگرام...";
            handler.postDelayed(discoveryTick, 5_000L);
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

    /** Starts discovery only; outbound message scheduling is independent. */
    public synchronized void startWordSearch() {
        if (!wordSearchRunning) {
            searchGeneration++;
            wordSearchRunning = true;
            prefs.edit().putBoolean(KEY_WORD_SEARCH_ENABLED, true).apply();
        }
        wordSearchStatus = "جستجوی بانک واژه در حال ادامه است.";
        if (!searchInFlight) {
            scheduleDiscovery(0L);
        }
        listener.onDataChanged();
    }

    public synchronized void stopWordSearch() {
        pauseWordSearch();
        wordSearchStatus = "جستجوی بانک واژه متوقف شد؛ مرحلهٔ جاری برای ادامه حفظ شد.";
        listener.onDataChanged();
    }

    public synchronized void restartWordSearch() {
        pauseWordSearch();
        smartQueue.restart();
        startWordSearch();
    }

    public synchronized boolean isWordSearchRunning() { return wordSearchRunning; }
    public synchronized String getWordSearchStatus() { return wordSearchStatus; }
    public synchronized int getMaxSearchStages() { return smartQueue.getMaxStages(); }
    public synchronized int getMinimumSearchLength() { return smartQueue.getMinimumLength(); }
    public synchronized List<WordSearchResult> getSearchHistory() { return smartQueue.getHistory(); }
    public synchronized List<WordSearchResult> getSearchHistory(int offset, int limit) {
        return smartQueue.getHistory(offset, limit);
    }
    public synchronized long getSearchHistoryCount() { return smartQueue.getHistoryCount(); }

    public synchronized void setSearchConfig(int maxStages, int minTrailingLength) {
        if (maxStages < 1 || maxStages > 100 || minTrailingLength < 2 || minTrailingLength > 96) {
            throw new IllegalArgumentException("مراحل باید ۱ تا ۱۰۰ و حد طول باید ۲ تا ۹۶ باشد.");
        }
        if (maxStages == getMaxSearchStages() && minTrailingLength == getMinimumSearchLength()) return;
        boolean resume = wordSearchRunning;
        pauseWordSearch();
        smartQueue.configure(maxStages, minTrailingLength);
        wordSearchStatus = "تنظیمات جستجو ذخیره شد؛ مراحل با تنظیمات جدید از ابتدا اجرا می‌شوند.";
        if (resume) startWordSearch();
        listener.onDataChanged();
    }

    public synchronized void onWordBankChanged() {
        smartQueue.size(); // Exact-content synchronization also invalidates removed/edited tasks.
        if (wordSearchRunning && !searchInFlight) {
            scheduleDiscovery(0L);
        }
        listener.onDataChanged();
    }

    private void pauseWordSearch() {
        wordSearchRunning = false;
        searchGeneration++;
        searchInFlight = false;
        smartQueue.cancelPending();
        prefs.edit().putBoolean(KEY_WORD_SEARCH_ENABLED, false).apply();
        handler.removeCallbacks(discoveryTick);
        clearSearchTimeout();
    }

    private void clearSearchTimeout() {
        if (searchTimeout != null) handler.removeCallbacks(searchTimeout);
        searchTimeout = null;
    }

    public synchronized boolean start() {
        if (enabled) {
            listener.onStatus("تنظیمات ثبت شد؛ ارسال در زمان بعدی مجاز انجام می‌شود.");
            listener.onDataChanged();
            return true;
        }
        runGeneration++;
        if (!wordSearchRunning) {
            searchGeneration++;
            smartQueue.cancelPending();
            clearSearchTimeout();
            searchInFlight = false;
        }
        sendInFlight = false;
        enabled = true;
        wordSearchRunning = true;
        prefs.edit().putBoolean(KEY_ENABLED, true).putBoolean(KEY_WORD_SEARCH_ENABLED, true).apply();
        handler.removeCallbacks(sendTick);
        handler.removeCallbacks(discoveryTick);
        listener.onStatus("هسته مرکزی فعال شد؛ جستجو شروع می‌شود و ارسال فقط با متن و گروه هدف مجاز انجام می‌شود.");
        if (!sendInFlight) handler.post(sendTick);
        if (!searchInFlight) scheduleDiscovery(5_000L);
        listener.onDataChanged();
        return true;
    }

    public synchronized void stop() {
        enabled = false;
        runGeneration++;
        pauseWordSearch();
        wordSearchStatus = "جستجوی بانک واژه متوقف شد؛ پیشرفت ذخیره شده است.";
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
        searchGeneration++;
        handler.removeCallbacks(sendTick);
        handler.removeCallbacks(discoveryTick);
        clearSearchTimeout();
        smartQueue.cancelPending();
        searchInFlight = false;
        sendInFlight = false;
    }

    private void runSendCycle() {
        final long generation;
        synchronized (this) {
            if (!enabled || sendInFlight) return;
            generation = runGeneration;
            long wait = searchNotBefore - System.currentTimeMillis();
            if (wait > 0L) {
                listener.onStatus("تلگرام محدودیت موقت اعمال کرده؛ ارسال بعدی پس از زمان مجاز انجام می‌شود.");
                handler.postDelayed(sendTick, wait);
                return;
            }
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
                long floodWait = parseFloodWaitMillis(resultMessage);
                if (floodWait > 0L) {
                    synchronized (CentralCore.this) {
                        searchNotBefore = Math.max(searchNotBefore, System.currentTimeMillis() + floodWait);
                        prefs.edit().putLong(KEY_SEARCH_NOT_BEFORE, searchNotBefore).apply();
                        handler.removeCallbacks(discoveryTick);
                        wordSearchStatus = "محدودیت Telegram؛ جستجو پس از پایان انتظار مجاز ادامه می‌یابد.";
                    }
                    listener.onStatus("تلگرام محدودیت موقت اعمال کرده؛ ارسال بعدی پس از زمان مجاز انجام می‌شود.");
                    handler.postDelayed(sendTick, floodWait);
                    scheduleDiscovery(floodWait);
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
        if (wordSearchRunning) scheduleDiscovery(0L);
        handler.postDelayed(sendTick, duration);
    }

    private void runDiscoveryStep() {
        final long generation;
        final long token;
        final SmartSearchQueue.SearchTask task;
        synchronized (this) {
            if (!wordSearchRunning || searchInFlight) return;
            long wait = searchNotBefore - System.currentTimeMillis();
            if (wait > 0L) {
                wordSearchStatus = "محدودیت Telegram؛ ادامهٔ مرحله پس از " + ((wait + 999L) / 1000L) + " ثانیه.";
                listener.onDataChanged();
                scheduleDiscovery(wait);
                return;
            }
            if (!telegram.isReadyForSending()) {
                wordSearchStatus = "در انتظار اتصال کامل و ورود به تلگرام؛ پیشرفت حفظ شده است.";
                listener.onDataChanged();
                scheduleDiscovery(SEARCH_STEP_MS);
                return;
            }
            task = smartQueue.nextTask();
            if (task == null) {
                pauseWordSearch();
                wordSearchStatus = "جستجوی بانک واژه کامل شد: " + smartQueue.completedWordCount()
                        + " از " + smartQueue.size() + " واژه؛ نتیجه‌ها و خطاها در سوابق ذخیره شدند.";
                listener.onStatus(wordSearchStatus);
                listener.onDataChanged();
                return;
            }
            generation = searchGeneration;
            token = ++searchRequestToken;
            searchInFlight = true;
            wordSearchStatus = "واژه «" + task.originalWord + "»؛ مرحله " + task.stage + " از "
                    + task.totalStages + ": «" + task.query + "»؛ " + smartQueue.completedWordCount()
                    + " از " + smartQueue.size() + " واژه کامل شده.";
            searchTimeout = () -> completeDiscovery(task, generation, token, false, 0, 0,
                    "SEARCH_TIMEOUT: پاسخ جستجوی تلگرام در زمان مجاز دریافت نشد.", Collections.emptyList());
            handler.postDelayed(searchTimeout, SEARCH_TIMEOUT_MS);
        }
        listener.onStatus(wordSearchStatus);
        listener.onDataChanged();
        LOG.fine("request token=" + token + " stage=" + task.stage
                + "/" + task.totalStages + " queryLength=" + task.query.length());
        TelegramClientManager.DiscoveryCallback callback = new TelegramClientManager.DiscoveryCallback() {
            @Override
            public void onResult(boolean success, int newItems, int totalItems, String resultMessage) {
                onDetailedResult(success, newItems, totalItems, resultMessage, Collections.emptyList());
            }

            @Override
            public void onDetailedResult(boolean success, int newItems, int totalItems,
                                         String resultMessage, List<Long> resultIds) {
                handler.post(() -> completeDiscovery(task, generation, token, success,
                        newItems, totalItems, resultMessage, resultIds));
            }
        };
        try {
            telegram.discoverPublicGroupsForReview(task.query, callback);
        } catch (RuntimeException error) {
            LOG.log(Level.WARNING, "Search transport failed token=" + token, error);
            callback.onResult(false, 0, 0, "SEARCH_TRANSPORT_ERROR: " + error.getClass().getSimpleName());
        }
    }

    private synchronized void completeDiscovery(SmartSearchQueue.SearchTask task, long generation,
                                                long token, boolean success, int newItems,
                                                int totalItems, String resultMessage, List<Long> resultIds) {
        if (!wordSearchRunning || generation != searchGeneration
                || token != searchRequestToken || !searchInFlight) return;
        searchInFlight = false;
        nextSearchAt = System.currentTimeMillis() + SEARCH_STEP_MS;
        clearSearchTimeout();
        if (!smartQueue.isCurrent(task)) {
            wordSearchStatus = "واژهٔ جاری تغییر کرد؛ جستجو از واژه‌های موجود ادامه می‌یابد.";
            listener.onDataChanged();
            scheduleDiscovery(SEARCH_STEP_MS);
            return;
        }
        long floodWait = success ? 0L : parseFloodWaitMillis(resultMessage);
        if (floodWait > 0L) {
            searchNotBefore = Math.max(searchNotBefore, System.currentTimeMillis() + floodWait);
            prefs.edit().putLong(KEY_SEARCH_NOT_BEFORE, searchNotBefore).apply();
        }
        smartQueue.recordResult(task, success, newItems, totalItems, resultMessage, resultIds, floodWait > 0L);
        if (success) {
            wordSearchStatus = "مرحله " + task.stage + "، «" + task.query + "»: " + totalItems
                    + " گروه مرتبط، " + newItems + " مورد جدید؛ نتیجه ذخیره شد.";
        } else {
            wordSearchStatus = "خطا در مرحله " + task.stage + "، «" + task.query + "»: " + resultMessage;
            if (floodWait > 0L) {
                wordSearchStatus += "؛ همین مرحله پس از پایان محدودیت دوباره اجرا می‌شود.";
            }
            LOG.warning("Search failed token=" + token + " stage=" + task.stage
                    + " retry=" + (floodWait > 0L) + " message=" + resultMessage);
        }
        listener.onStatus(wordSearchStatus);
        listener.onDataChanged();
        if (floodWait == 0L && smartQueue.completedWordCount() == smartQueue.size()) {
            pauseWordSearch();
            wordSearchStatus += "\nپایان بانک واژه؛ همهٔ " + smartQueue.size() + " واژه پردازش شدند.";
            listener.onStatus(wordSearchStatus);
            listener.onDataChanged();
        } else {
            scheduleDiscovery(Math.max(SEARCH_STEP_MS, searchNotBefore - System.currentTimeMillis()));
        }
    }

    private synchronized void scheduleDiscovery(long delay) {
        if (wordSearchRunning) {
            handler.removeCallbacks(discoveryTick);
            long pacedDelay = Math.max(delay,
                    Math.max(nextSearchAt, searchNotBefore) - System.currentTimeMillis());
            if (pacedDelay <= 0L) handler.post(discoveryTick);
            else handler.postDelayed(discoveryTick, pacedDelay);
        }
    }

    public synchronized int completedSearchWordCount() {
        return smartQueue.completedWordCount();
    }

    public synchronized boolean isWordSearchWaitingForTelegram() {
        return wordSearchRunning && (!telegram.isReadyForSending()
                || searchNotBefore > System.currentTimeMillis());
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

    private static long parseFloodWaitMillis(String message) {
        if (message == null) return 0L;
        Matcher matcher = FLOOD_WAIT.matcher(message);
        if (!matcher.find()) {
            matcher = RETRY_AFTER.matcher(message);
            if (!matcher.find()) return RATE_LIMIT.matcher(message).find() ? 60_000L : 0L;
        }

        try {
            long seconds = Long.parseLong(matcher.group(1));
            return Math.max(60_000L, Math.multiplyExact(seconds, 1000L));
        } catch (ArithmeticException | NumberFormatException ignored) {
            return 60_000L;
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
