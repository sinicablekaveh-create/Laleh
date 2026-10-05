package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TelegramClientManager {
    public enum AuthStep {
        IDLE, WAIT_PARAMETERS, PHONE, CODE, PASSWORD, EMAIL_ADDRESS, EMAIL_CODE,
        OTHER_DEVICE, REGISTRATION, READY, LOGGING_OUT, CLOSED
    }

    public interface Listener {
        void onAuthStep(AuthStep step, String message);
        void onProxyStatus(String message);
        void onConnectionStatus(String message, boolean ready);
        void onTargetGroupChanged(long chatId);
        default void onTargetGroupsLoadChanged() { }
        void onFoundGroupsChanged();
        void onObservedUsersChanged();
        void onError(String message);
        void onMessageText(String text);
    }

    private static final String DISCOVERY_PREFS = "telegram_discovery";
    private static final String KEY_GROUPS_JSON = "groups_json";
    private static final String KEY_OBSERVED_USERS_JSON = "observed_users_json";
    private static final String LEGACY_CONTACTS_JSON = "contacts_json";

    private static final Object TDJNI_LOCK = new Object();
    private static volatile boolean tdjniLoaded = false;

    private final Context context;
    private volatile Listener listener;
    private final SharedPreferences discoveryPrefs;
    private volatile AuthStep currentStep = AuthStep.IDLE;
    private volatile Client client;
    private int apiId;
    private String apiHash = "";
    private volatile ProxyLinkParser.ProxyConfig pendingProxy;
    private final Map<Long, GroupInfo> targetGroups = new ConcurrentHashMap<>();
    private final Map<Long, GroupInfo> foundGroups = new ConcurrentHashMap<>();
    private final Map<Long, ContactInfo> observedUsers = new ConcurrentHashMap<>();
    private final Map<Long, TdApi.User> userCache = new ConcurrentHashMap<>();
    private final Map<Long, TdApi.Chat> chatCache = new ConcurrentHashMap<>();
    private final Map<Long, TdApi.Supergroup> supergroupCache = new ConcurrentHashMap<>();
    private final Map<Long, TdApi.BasicGroup> basicGroupCache = new ConcurrentHashMap<>();
    private final Map<Long, Long> supergroupChatIds = new ConcurrentHashMap<>();
    private final Map<Long, Long> basicGroupChatIds = new ConcurrentHashMap<>();
    private final java.util.Set<Long> directSenderIds = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ExecutorService runtimeExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.concurrent.ExecutorService storageExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean starting =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean persistDirty =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean persistRunning =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private int nextGroupNumber = 1;
    private int nextContactNumber = 1;
    private volatile String connectionStatusMessage = "تلگرام هنوز شروع نشده است.";
    private volatile boolean connectionReady = false;
    private final java.util.concurrent.atomic.AtomicBoolean targetGroupsLoading =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicInteger targetLoadGeneration =
            new java.util.concurrent.atomic.AtomicInteger();
    private volatile String targetGroupsLoadMessage = "برای دریافت گروه‌های عضو، ابتدا وارد حساب تلگرام شو.";


    public static final class GroupInfo {
        public final int number;
        public final long id;
        public final String title;
        public final String link;
        public final int memberCount;
        public final String status;
        public final boolean canSend;
        public final boolean canSendPhotos;
        public final boolean discoveredBySearch;

        GroupInfo(
                int number,
                long id,
                String title,
                String link,
                int memberCount,
                String status,
                boolean canSend,
                boolean discoveredBySearch
        ) {
            this(number, id, title, link, memberCount, status, canSend, canSend, discoveredBySearch);
        }

        GroupInfo(
                int number, long id, String title, String link, int memberCount,
                String status, boolean canSend, boolean canSendPhotos, boolean discoveredBySearch
        ) {
            this.number = Math.max(0, number);
            this.id = id;
            this.title = cleanLabel(title, String.valueOf(id));
            this.link = cleanLabel(link, "لینک عمومی در دسترس نیست");
            this.memberCount = Math.max(0, memberCount);
            this.status = cleanLabel(status, "وضعیت نامشخص");
            this.canSend = canSend;
            this.canSendPhotos = canSendPhotos;
            this.discoveredBySearch = discoveredBySearch;
        }

        GroupInfo(int number, long id, String title, String link, int memberCount, String status, boolean canSend) {
            this(number, id, title, link, memberCount, status, canSend, false);
        }

        GroupInfo(long id, String title, String link, int memberCount, String status, boolean canSend) {
            this(0, id, title, link, memberCount, status, canSend, false);
        }

        GroupInfo(
                long id,
                String title,
                String link,
                int memberCount,
                String status,
                boolean canSend,
                boolean discoveredBySearch
        ) {
            this(0, id, title, link, memberCount, status, canSend, discoveredBySearch);
        }
    }

    public static final class ContactInfo {
        public final int number;
        public final long id;
        public final String name;
        public final String phone;

        ContactInfo(int number, long id, String name, String phone) {
            this.number = Math.max(0, number);
            this.id = id;
            this.name = cleanLabel(name, String.valueOf(id));
            this.phone = phone == null ? "" : phone.trim();
        }

        ContactInfo(long id, String name, String phone) {
            this(0, id, name, phone);
        }
    }

    public interface SendCallback {
        void onResult(boolean success, String message);
    }

    public interface DiscoveryCallback {
        void onResult(boolean success, int newItems, int totalItems, String message);
    }

    public TelegramClientManager(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.discoveryPrefs = this.context.getSharedPreferences(DISCOVERY_PREFS, Context.MODE_PRIVATE);
        loadDiscovery();
    }

    public void setListener(Listener listener) {
        if (listener != null) {
            this.listener = listener;
        }
    }

    public void start(int apiId, String apiHash) {
        if (apiId <= 0 || apiHash == null || apiHash.trim().isEmpty()) {
            listener.onError("API ID و API Hash معتبر وارد کنید.");
            return;
        }

        if (!starting.compareAndSet(false, true)) {
            listener.onError("راه‌اندازی تلگرام در حال انجام است.");
            return;
        }

        final int requestedApiId = apiId;
        final String requestedApiHash = apiHash.trim();
        currentStep = AuthStep.WAIT_PARAMETERS;
        listener.onAuthStep(currentStep, "در حال راه‌اندازی اتصال تلگرام...");

        runtimeExecutor.execute(() -> {
            try {
                closeExistingClientForRestart();

                synchronized (TelegramClientManager.this) {
                    TelegramClientManager.this.apiId = requestedApiId;
                    TelegramClientManager.this.apiHash = requestedApiHash;
                }

                ensureTdjniLoaded();
                Client.execute(new TdApi.SetLogVerbosityLevel(1));

                Client created = Client.create(
                        this::onUpdate,
                        error -> listener.onError("خطای TDLib: " + safeMessage(error)),
                        error -> listener.onError("خطای TDLib: " + safeMessage(error))
                );

                synchronized (TelegramClientManager.this) {
                    client = created;
                }
                if (currentStep == AuthStep.READY) refreshTargetGroups();

                ProxyLinkParser.ProxyConfig queued = pendingProxy;
                if (queued != null) {
                    applyProxy(queued);
                }
            } catch (Throwable error) {
                synchronized (TelegramClientManager.this) {
                    client = null;
                }
                currentStep = AuthStep.IDLE;
                listener.onError("راه‌اندازی TDLib ناموفق بود: " + safeMessage(error));
            } finally {
                starting.set(false);
            }
        });
    }

    private static void ensureTdjniLoaded() {
        if (tdjniLoaded) return;

        synchronized (TDJNI_LOCK) {
            if (tdjniLoaded) return;
            System.loadLibrary("tdjni");
            tdjniLoaded = true;
        }
    }

    private synchronized void closeExistingClientForRestart() {
        Client local = client;
        client = null;
        connectionReady = false;
        targetLoadGeneration.incrementAndGet();
        targetGroupsLoading.set(false);
        targetGroups.clear();
        chatCache.clear();
        supergroupCache.clear();
        basicGroupCache.clear();
        supergroupChatIds.clear();
        basicGroupChatIds.clear();
        targetGroupsLoadMessage = "در انتظار ورود و دریافت گروه‌های حساب...";
        listener.onTargetGroupsLoadChanged();

        if (local != null) {
            try {
                local.send(new TdApi.Close(), result -> { });
            } catch (Throwable ignored) {
            }
        }
    }

    public AuthStep getCurrentStep() {
        return currentStep;
    }

    public void emitCurrentConnectionStatus() {
        listener.onConnectionStatus(connectionStatusMessage, connectionReady);
    }


    public boolean isReadyForSending() {
        return currentStep == AuthStep.READY && connectionReady && client != null;
    }

    public List<GroupInfo> getTargetGroups() {
        List<GroupInfo> result = new ArrayList<>(targetGroups.values());
        result.sort(Comparator.comparing(info -> info.title.toLowerCase(java.util.Locale.ROOT)));
        return result;
    }

    public GroupInfo getTargetGroup(long chatId) {
        return targetGroups.get(chatId);
    }

    public boolean isLoadingTargetGroups() {
        return targetGroupsLoading.get();
    }

    public String getTargetGroupsLoadMessage() {
        return targetGroupsLoadMessage;
    }

    public void refreshTargetGroups() {
        Client local = client;
        if (local == null || currentStep != AuthStep.READY) {
            targetGroupsLoadMessage = "برای انتخاب گروه‌های عضو، ابتدا وارد حساب تلگرام شو.";
            listener.onTargetGroupsLoadChanged();
            return;
        }
        if (!targetGroupsLoading.compareAndSet(false, true)) return;
        int generation = targetLoadGeneration.incrementAndGet();
        java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(2);
        java.util.concurrent.atomic.AtomicReference<String> error = new java.util.concurrent.atomic.AtomicReference<>("");
        targetGroupsLoadMessage = "در حال دریافت گروه‌های عضو از فهرست اصلی و آرشیو...";
        listener.onTargetGroupsLoadChanged();
        runtimeExecutor.execute(() -> loadTargetChatList(local, new TdApi.ChatListMain(), generation, pending, error));
        runtimeExecutor.execute(() -> loadTargetChatList(local, new TdApi.ChatListArchive(), generation, pending, error));
    }

    private void loadTargetChatList(
            Client local, TdApi.ChatList list, int generation,
            java.util.concurrent.atomic.AtomicInteger pending,
            java.util.concurrent.atomic.AtomicReference<String> error
    ) {
        if (generation != targetLoadGeneration.get()) return;
        if (local != client || currentStep != AuthStep.READY) {
            error.compareAndSet("", "اتصال تلگرام آماده نیست؛ فهرست را دوباره باز کن.");
            finishTargetChatList(generation, pending, error);
            return;
        }
        local.send(new TdApi.LoadChats(list, 100), result -> {
            if (generation != targetLoadGeneration.get()) return;
            if (result instanceof TdApi.Ok) {
                runtimeExecutor.execute(() -> loadTargetChatList(local, list, generation, pending, error));
                return;
            }
            if (result instanceof TdApi.Error) {
                TdApi.Error failure = (TdApi.Error) result;
                if (failure.code != 404) error.compareAndSet("", "Telegram " + failure.code + ": " + failure.message);
            } else {
                error.compareAndSet("", "پاسخ دریافت گروه‌ها نامعتبر بود.");
            }
            finishTargetChatList(generation, pending, error);
        });
    }

    private void finishTargetChatList(
            int generation, java.util.concurrent.atomic.AtomicInteger pending,
            java.util.concurrent.atomic.AtomicReference<String> error
    ) {
        if (generation != targetLoadGeneration.get() || pending.decrementAndGet() != 0) return;
        targetGroupsLoading.set(false);
        targetGroupsLoadMessage = error.get().isEmpty()
                ? targetGroups.size() + " گروه عضو حساب برای انتخاب دریافت شد."
                : "دریافت گروه‌ها کامل نشد: " + error.get();
        listener.onTargetGroupsLoadChanged();
    }

    public List<GroupInfo> getFoundGroups() {
        List<GroupInfo> result = new ArrayList<>(foundGroups.values());
        result.sort(Comparator.comparingInt(info -> info.number));
        return result;
    }

    public List<ContactInfo> getTelegramContacts() {
        List<ContactInfo> result = new ArrayList<>(observedUsers.values());
        result.sort(Comparator.comparingInt(info -> info.number));
        return result;
    }

    public int getFoundGroupCount() {
        return foundGroups.size();
    }

    public int getObservedUserCount() {
        return observedUsers.size();
    }

    public GroupInfo getGroup(long chatId) {
        GroupInfo target = targetGroups.get(chatId);
        return target != null ? target : foundGroups.get(chatId);
    }

    public void sendPhotoToChat(
            long chatId,
            String photoPath,
            String caption,
            SendCallback callback
    ) {
        Client local = client;
        if (local == null || currentStep != AuthStep.READY) {
            if (callback != null) callback.onResult(false, "تلگرام آماده ارسال نیست.");
            return;
        }

        GroupInfo target = targetGroups.get(chatId);
        if (target == null || !target.canSendPhotos) {
            if (callback != null) callback.onResult(false, "این گروه عضو حساب نیست یا اجازهٔ ارسال عکس ندارد.");
            return;
        }

        File photoFile = photoPath == null ? null : new File(photoPath);
        if (photoFile == null || !photoFile.isFile() || photoFile.length() == 0L) {
            if (callback != null) callback.onResult(false, "فایل عکس پیدا نشد.");
            return;
        }

        final TdApi.InputMessageContent content;
        try {
            Class<?> inputFileLocalClass =
                    Class.forName("org.drinkless.tdlib.TdApi$InputFileLocal");
            Object inputFile = inputFileLocalClass
                    .getConstructor(String.class)
                    .newInstance(photoFile.getAbsolutePath());

            Class<?> inputMessagePhotoClass =
                    Class.forName("org.drinkless.tdlib.TdApi$InputMessagePhoto");
            Object photoContent = inputMessagePhotoClass
                    .getDeclaredConstructor()
                    .newInstance();

            Field photoField = inputMessagePhotoClass.getField("photo");
            Object photoDetails = photoContent;
            if (photoField.getType().isInstance(inputFile)) {
                photoField.set(photoContent, inputFile);
            } else {
                photoDetails = photoField.getType().getDeclaredConstructor().newInstance();
                setField(photoDetails, "photo", inputFile);
                photoField.set(photoContent, photoDetails);
            }

            if (hasField(inputMessagePhotoClass, "caption")) {
                setField(
                        photoContent,
                        "caption",
                        new TdApi.FormattedText(
                                caption == null ? "" : caption.trim(),
                                null
                        )
                );
            }
            if (hasField(photoDetails.getClass(), "width")) {
                setField(photoDetails, "width", 0);
            }
            if (hasField(photoDetails.getClass(), "height")) {
                setField(photoDetails, "height", 0);
            }
            if (hasField(inputMessagePhotoClass, "ttl")) {
                setField(photoContent, "ttl", 0);
            }
            if (hasField(photoDetails.getClass(), "addedStickerFileIds")) {
                Field stickers = photoDetails.getClass().getField("addedStickerFileIds");
                stickers.set(photoDetails, Array.newInstance(stickers.getType().getComponentType(), 0));
            }
            if (hasField(inputMessagePhotoClass, "showCaptionAboveMedia")) {
                setField(photoContent, "showCaptionAboveMedia", false);
            }
            if (hasField(inputMessagePhotoClass, "hasSpoiler")) {
                setField(photoContent, "hasSpoiler", false);
            }

            content = (TdApi.InputMessageContent) photoContent;
        } catch (Throwable error) {
            if (callback != null) {
                callback.onResult(
                        false,
                        "ساخت پیام عکس ناموفق بود: " + safeMessage(error)
                );
            }
            return;
        }

        local.send(
                new TdApi.SendMessage(chatId, null, null, null, null, content),
                result -> {
                    if (result instanceof TdApi.Error) {
                        TdApi.Error error = (TdApi.Error) result;
                        if (callback != null) {
                            callback.onResult(false, error.code + ": " + error.message);
                        }
                    } else {
                        if (callback != null) callback.onResult(true, "عکس و متن ارسال شد.");
                    }
                }
        );
    }

    public void sendTextToChat(long chatId, String message, SendCallback callback) {
        Client local = client;
        if (local == null || currentStep != AuthStep.READY) {
            if (callback != null) callback.onResult(false, "تلگرام آماده ارسال نیست.");
            return;
        }
        GroupInfo target = targetGroups.get(chatId);
        if (target == null || !target.canSend) {
            if (callback != null) callback.onResult(false, "این گروه عضو حساب نیست یا اجازهٔ ارسال متن ندارد.");
            return;
        }
        String clean = message == null ? "" : message.trim();
        if (clean.isEmpty()) {
            if (callback != null) callback.onResult(false, "متن پیام خالی است.");
            return;
        }

        TdApi.InputMessageContent content = new TdApi.InputMessageText(
                new TdApi.FormattedText(clean, null),
                null,
                true
        );

        local.send(
                new TdApi.SendMessage(chatId, null, null, null, null, content),
                result -> {
                    if (result instanceof TdApi.Error) {
                        TdApi.Error error = (TdApi.Error) result;
                        if (callback != null) callback.onResult(false, error.code + ": " + error.message);
                    } else {
                        if (callback != null) callback.onResult(true, "ارسال شد.");
                    }
                }
        );
    }

    public void discoverPublicGroupsForReview(String query, DiscoveryCallback callback) {
        Client local = client;
        String clean = query == null ? "" : query.trim();
        if (local == null || currentStep != AuthStep.READY) {
            if (callback != null) callback.onResult(false, 0, 0, "تلگرام آماده جستجو نیست.");
            return;
        }
        if (clean.length() < 2) {
            if (callback != null) callback.onResult(false, 0, 0, "عبارت جستجو کوتاه است.");
            return;
        }

        final TdApi.Function request;
        try {
            Class<?> type = Class.forName("org.drinkless.tdlib.TdApi$SearchPublicChats");
            Object instance = null;

            for (Constructor<?> constructor : type.getConstructors()) {
                Class<?>[] params = constructor.getParameterTypes();
                if (params.length == 1 && params[0] == String.class) {
                    instance = constructor.newInstance(clean);
                    break;
                }
            }

            if (instance == null) {
                instance = type.getDeclaredConstructor().newInstance();
                if (hasField(type, "query")) {
                    setField(instance, "query", clean);
                } else if (hasField(type, "usernamePrefix")) {
                    setField(instance, "usernamePrefix", clean);
                } else {
                    throw new IllegalStateException("فیلد جستجوی عمومی TDLib پیدا نشد.");
                }
            }

            request = (TdApi.Function) instance;
        } catch (Throwable error) {
            if (callback != null) {
                callback.onResult(false, 0, 0, "ساخت جستجوی عمومی ناموفق بود: " + safeMessage(error));
            }
            return;
        }

        local.send(request, result -> {
            if (result instanceof TdApi.Error) {
                TdApi.Error error = (TdApi.Error) result;
                if (callback != null) {
                    callback.onResult(false, 0, 0, "Telegram " + error.code + ": " + error.message);
                }
                return;
            }

            if (!(result instanceof TdApi.Chats)) {
                if (callback != null) callback.onResult(true, 0, 0, "نتیجه گروهی پیدا نشد.");
                return;
            }

            long[] ids = ((TdApi.Chats) result).chatIds;
            if (ids == null || ids.length == 0) {
                if (callback != null) callback.onResult(true, 0, 0, "نتیجه گروهی پیدا نشد.");
                return;
            }

            int newItems = 0;
            int validItems = 0;
            for (long chatId : ids) {
                TdApi.Chat chat = chatCache.get(chatId);
                if (chat == null || !isGroupChat(chat)) continue;
                validItems++;
                boolean isNew = !foundGroups.containsKey(chat.id);
                captureSearchResult(chat);
                if (isNew) newItems++;
            }
            if (callback != null) {
                callback.onResult(true, newItems, validItems, "جستجوی عمومی برای نمایش کامل شد.");
            }
        });
    }

    public void searchKnownGroups(String query, DiscoveryCallback callback) {
        Client local = client;
        String clean = query == null ? "" : query.trim();
        if (local == null || currentStep != AuthStep.READY) {
            if (callback != null) callback.onResult(false, 0, 0, "تلگرام آماده جستجو نیست.");
            return;
        }
        if (clean.length() < 2) {
            if (callback != null) callback.onResult(false, 0, 0, "عبارت جستجو کوتاه است.");
            return;
        }

        local.send(new TdApi.SearchChats(clean, null, 50), result -> {
            if (result instanceof TdApi.Error) {
                TdApi.Error error = (TdApi.Error) result;
                if (callback != null) {
                    callback.onResult(false, 0, 0, "Telegram " + error.code + ": " + error.message);
                }
                return;
            }

            if (!(result instanceof TdApi.Chats)) {
                if (callback != null) callback.onResult(true, 0, 0, "گروهی در چت‌های حساب پیدا نشد.");
                return;
            }

            long[] ids = ((TdApi.Chats) result).chatIds;
            if (ids == null || ids.length == 0) {
                if (callback != null) callback.onResult(true, 0, 0, "گروهی در چت‌های حساب پیدا نشد.");
                return;
            }

            int newItems = 0;
            int validItems = 0;
            for (long chatId : ids) {
                TdApi.Chat chat = chatCache.get(chatId);
                if (chat == null || !isGroupChat(chat)) continue;
                validItems++;
                boolean wasKnown = targetGroups.containsKey(chat.id);
                inspectTargetGroup(chat);
                if (!wasKnown && targetGroups.containsKey(chat.id)) newItems++;
            }
            if (callback != null) {
                callback.onResult(true, newItems, validItems, "جستجوی گروه‌های موجود در حساب کامل شد.");
            }
        });
    }

    public void setProxyFromLink(String link) {
        final ProxyLinkParser.ProxyConfig config;
        try {
            config = ProxyLinkParser.parse(link);
        } catch (IllegalArgumentException error) {
            listener.onError(error.getMessage());
            return;
        }

        pendingProxy = config;
        if (client == null) {
            listener.onProxyStatus(
                    "پروکسی آماده شد. بعد از زدن «شروع اتصال» به‌صورت خودکار فعال می‌شود."
            );
            return;
        }

        applyProxy(config);
    }

    public void submitPhone(String phone) {
        if (client == null) {
            listener.onError("ابتدا اتصال را شروع کنید.");
            return;
        }
        if (currentStep != AuthStep.PHONE) {
            listener.onError("تلگرام در حال حاضر منتظر شماره تلفن نیست.");
            return;
        }

        String value = phone == null ? "" : phone.trim();
        if (value.isEmpty() || !value.startsWith("+")) {
            listener.onError("شماره را با کد کشور وارد کنید؛ مثال: +994...");
            return;
        }

        sendAuth(new TdApi.SetAuthenticationPhoneNumber(value, null));
    }

    public void submitAuthValue(String value) {
        if (client == null) {
            listener.onError("ابتدا اتصال را شروع کنید.");
            return;
        }

        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) {
            listener.onError("مقدار موردنیاز را وارد کنید.");
            return;
        }

        switch (currentStep) {
            case CODE:
                sendAuth(new TdApi.CheckAuthenticationCode(clean));
                break;
            case PASSWORD:
                sendAuth(new TdApi.CheckAuthenticationPassword(clean));
                break;
            case EMAIL_ADDRESS:
                sendAuth(new TdApi.SetAuthenticationEmailAddress(clean));
                break;
            case EMAIL_CODE:
                sendAuth(new TdApi.CheckAuthenticationEmailCode(
                        new TdApi.EmailAddressAuthenticationCode(clean)
                ));
                break;
            default:
                listener.onError("در این مرحله ورودی اضافی لازم نیست.");
        }
    }

    private void applyProxy(ProxyLinkParser.ProxyConfig config) {
        Client local = client;
        if (local == null) {
            listener.onProxyStatus("پروکسی ذخیره شد و با شروع اتصال فعال می‌شود.");
            return;
        }

        final TdApi.Function request;
        try {
            request = buildAddProxyFunction(config);
        } catch (Throwable error) {
            listener.onError("ساخت تنظیمات پروکسی ناموفق بود: " + safeMessage(error));
            return;
        }

        listener.onProxyStatus("در حال فعال‌سازی پروکسی " + config.server + ":" + config.port + " ...");
        local.send(request, result -> {
            if (result instanceof TdApi.Error) {
                TdApi.Error error = (TdApi.Error) result;
                listener.onError("Proxy " + error.code + ": " + error.message);
            } else {
                pendingProxy = config;
                String type = config.type == ProxyLinkParser.Type.MTPROTO ? "MTProto" : "SOCKS5";
                listener.onProxyStatus(type + " فعال شد: " + config.server + ":" + config.port);
            }
        });
    }

    private TdApi.Function buildAddProxyFunction(ProxyLinkParser.ProxyConfig config) throws Exception {
        Object proxyType = buildProxyType(config);
        Class<?> addProxyClass = Class.forName("org.drinkless.tdlib.TdApi$AddProxy");

        for (Constructor<?> constructor : addProxyClass.getConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();

            if (params.length == 4
                    && params[0] == String.class
                    && (params[1] == int.class || params[1] == Integer.class)
                    && (params[2] == boolean.class || params[2] == Boolean.class)) {
                return (TdApi.Function) constructor.newInstance(
                        config.server, config.port, true, proxyType
                );
            }

            if (params.length == 2
                    && (params[1] == boolean.class || params[1] == Boolean.class)) {
                Object proxy = buildProxyObject(config, proxyType);
                if (params[0].isAssignableFrom(proxy.getClass())) {
                    return (TdApi.Function) constructor.newInstance(proxy, true);
                }
            }
        }

        Object request = addProxyClass.getDeclaredConstructor().newInstance();

        if (hasField(addProxyClass, "server")) {
            setField(request, "server", config.server);
            setField(request, "port", config.port);
            setField(request, "enable", true);
            setField(request, "type", proxyType);
        } else {
            Object proxy = buildProxyObject(config, proxyType);
            setField(request, "proxy", proxy);
            setField(request, "enable", true);
        }

        return (TdApi.Function) request;
    }

    private Object buildProxyType(ProxyLinkParser.ProxyConfig config) throws Exception {
        String className = config.type == ProxyLinkParser.Type.MTPROTO
                ? "org.drinkless.tdlib.TdApi$ProxyTypeMtproto"
                : "org.drinkless.tdlib.TdApi$ProxyTypeSocks5";

        Class<?> typeClass = Class.forName(className);

        if (config.type == ProxyLinkParser.Type.MTPROTO) {
            try {
                return typeClass.getConstructor(String.class).newInstance(config.secret);
            } catch (NoSuchMethodException ignored) {
                Object type = typeClass.getDeclaredConstructor().newInstance();
                setField(type, "secret", config.secret);
                return type;
            }
        }

        try {
            return typeClass.getConstructor(String.class, String.class)
                    .newInstance(config.username, config.password);
        } catch (NoSuchMethodException ignored) {
            Object type = typeClass.getDeclaredConstructor().newInstance();
            setField(type, "username", config.username);
            setField(type, "password", config.password);
            return type;
        }
    }

    private Object buildProxyObject(ProxyLinkParser.ProxyConfig config, Object proxyType) throws Exception {
        Class<?> proxyClass = Class.forName("org.drinkless.tdlib.TdApi$Proxy");

        for (Constructor<?> constructor : proxyClass.getConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length == 3
                    && params[0] == String.class
                    && (params[1] == int.class || params[1] == Integer.class)
                    && params[2].isAssignableFrom(proxyType.getClass())) {
                return constructor.newInstance(config.server, config.port, proxyType);
            }
        }

        Object proxy = proxyClass.getDeclaredConstructor().newInstance();
        setField(proxy, "server", config.server);
        setField(proxy, "port", config.port);
        setField(proxy, "type", proxyType);
        return proxy;
    }

    private static boolean hasField(Class<?> type, String name) {
        try {
            type.getField(name);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getField(name);
        field.set(target, value);
    }

    private void onUpdate(TdApi.Object object) {
        if (object instanceof TdApi.UpdateConnectionState) {
            handleConnectionState(((TdApi.UpdateConnectionState) object).state);
            return;
        }

        if (object instanceof TdApi.UpdateNewChat) {
            TdApi.Chat chat = ((TdApi.UpdateNewChat) object).chat;
            if (chat != null) {
                chatCache.put(chat.id, chat);
                if (isGroupChat(chat)) {
                    if (chat.type instanceof TdApi.ChatTypeSupergroup) {
                        supergroupChatIds.put(((TdApi.ChatTypeSupergroup) chat.type).supergroupId, chat.id);
                    } else if (chat.type instanceof TdApi.ChatTypeBasicGroup) {
                        basicGroupChatIds.put(((TdApi.ChatTypeBasicGroup) chat.type).basicGroupId, chat.id);
                    }
                    inspectTargetGroup(chat);
                }
            }
            return;
        }

        if (object instanceof TdApi.UpdateSupergroup) {
            TdApi.Supergroup group = ((TdApi.UpdateSupergroup) object).supergroup;
            if (group != null) {
                supergroupCache.put(group.id, group);
                Long chatId = supergroupChatIds.get(group.id);
                if (chatId != null) inspectTargetGroup(chatCache.get(chatId));
            }
            return;
        }

        if (object instanceof TdApi.UpdateBasicGroup) {
            TdApi.BasicGroup group = ((TdApi.UpdateBasicGroup) object).basicGroup;
            if (group != null) {
                basicGroupCache.put(group.id, group);
                Long chatId = basicGroupChatIds.get(group.id);
                if (chatId != null) inspectTargetGroup(chatCache.get(chatId));
            }
            return;
        }

        if (object instanceof TdApi.UpdateChatPermissions) {
            TdApi.UpdateChatPermissions update = (TdApi.UpdateChatPermissions) object;
            TdApi.Chat chat = chatCache.get(update.chatId);
            if (chat != null) {
                chat.permissions = update.permissions;
                inspectTargetGroup(chat);
            }
            return;
        }

        if (object instanceof TdApi.UpdateChatTitle) {
            TdApi.UpdateChatTitle update = (TdApi.UpdateChatTitle) object;
            TdApi.Chat cached = chatCache.get(update.chatId);
            if (cached != null) {
                cached.title = update.title;
            }

            String title = cleanLabel(update.title, String.valueOf(update.chatId));
            GroupInfo target = targetGroups.get(update.chatId);
            if (target != null && !target.title.equals(title)) {
                targetGroups.put(update.chatId, new GroupInfo(
                        target.number,
                        target.id,
                        title,
                        target.link,
                        target.memberCount,
                        target.status,
                        target.canSend,
                        target.canSendPhotos,
                        false
                ));
                listener.onTargetGroupChanged(update.chatId);
            }

            GroupInfo found = foundGroups.get(update.chatId);
            if (found != null && !found.title.equals(title)) {
                foundGroups.put(update.chatId, new GroupInfo(
                        found.number,
                        found.id,
                        title,
                        found.link,
                        found.memberCount,
                        found.status,
                        false,
                        true
                ));
                schedulePersistDiscovery();
                listener.onFoundGroupsChanged();
            }
            return;
        }

        if (object instanceof TdApi.UpdateUser) {
            TdApi.User user = ((TdApi.UpdateUser) object).user;
            if (user != null) {
                userCache.put(user.id, user);
                if (directSenderIds.contains(user.id)) {
                    storeDirectUserIfPhoneVisible(user);
                }
            }
            return;
        }

        if (object instanceof TdApi.UpdateAuthorizationState) {
            handleAuthorizationState(((TdApi.UpdateAuthorizationState) object).authorizationState);
            return;
        }

        if (object instanceof TdApi.UpdateNewMessage) {
            TdApi.Message message = ((TdApi.UpdateNewMessage) object).message;
            if (message != null) {
                observeDirectSender(message);

                if (message.content instanceof TdApi.MessageText) {
                    TdApi.MessageText content = (TdApi.MessageText) message.content;
                    if (content.text != null
                            && content.text.text != null
                            && !content.text.text.isEmpty()) {
                        listener.onMessageText(content.text.text);
                    }
                }
            }
        }
    }

    private void inspectTargetGroup(TdApi.Chat chat) {
        if (chat == null || !isGroupChat(chat)) return;
        Object meta = cachedGroupMetadata(chat);
        if (meta != null) {
            updateTargetFromMeta(chat, meta);
            updateFoundFromMeta(chat, meta);
        }
    }

    private Object cachedGroupMetadata(TdApi.Chat chat) {
        if (chat.type instanceof TdApi.ChatTypeSupergroup) {
            return supergroupCache.get(((TdApi.ChatTypeSupergroup) chat.type).supergroupId);
        }
        if (chat.type instanceof TdApi.ChatTypeBasicGroup) {
            return basicGroupCache.get(((TdApi.ChatTypeBasicGroup) chat.type).basicGroupId);
        }
        return null;
    }

    private void updateTargetFromMeta(TdApi.Chat chat, Object meta) {
        Object statusObject = readObjectField(meta, "status");
        boolean joined = isJoinedGroupStatus(statusObject);
        boolean canSend = chatAllowsMessages(chat, statusObject, false);
        boolean canSendPhotos = chatAllowsMessages(chat, statusObject, true);

        if (!joined) {
            if (targetGroups.remove(chat.id) != null) {
                listener.onTargetGroupChanged(chat.id);
            }
            return;
        }

        GroupInfo existing = targetGroups.get(chat.id);
        int memberCount = readIntField(
                meta,
                "memberCount",
                existing == null ? 0 : existing.memberCount
        );
        String username = extractPublicUsername(meta);
        String link = username.isEmpty()
                ? (existing == null ? "" : existing.link)
                : "https://t.me/" + username;

        GroupInfo updated = new GroupInfo(
                0,
                chat.id,
                chat.title,
                link,
                memberCount,
                describeMemberStatus(statusObject),
                canSend,
                canSendPhotos,
                false
        );
        if (sameGroup(existing, updated)) return;
        targetGroups.put(chat.id, updated);
        listener.onTargetGroupChanged(chat.id);
    }

    private void captureSearchResult(TdApi.Chat chat) {
        if (chat == null || !isGroupChat(chat)) return;

        GroupInfo existing = foundGroups.get(chat.id);
        storeFoundGroup(new GroupInfo(
                existing == null ? 0 : existing.number,
                chat.id,
                chat.title,
                existing == null ? "" : existing.link,
                existing == null ? 0 : existing.memberCount,
                existing == null ? "نتیجه جستجوی بانک کلمات" : existing.status,
                false,
                true
        ));

        Object meta = cachedGroupMetadata(chat);
        if (meta != null) {
            updateFoundFromMeta(chat, meta);
        }
    }

    private void updateFoundFromMeta(TdApi.Chat chat, Object meta) {
        GroupInfo existing = foundGroups.get(chat.id);
        if (existing == null) return;

        int memberCount = readIntField(meta, "memberCount", existing.memberCount);
        Object statusObject = readObjectField(meta, "status");
        String status = statusObject == null
                ? existing.status
                : describeMemberStatus(statusObject);

        String username = extractPublicUsername(meta);
        String link = username.isEmpty()
                ? existing.link
                : "https://t.me/" + username;

        storeFoundGroup(new GroupInfo(
                existing.number,
                chat.id,
                chat.title,
                link,
                memberCount,
                status,
                false,
                true
        ));
    }

    private synchronized boolean storeFoundGroup(GroupInfo info) {
        GroupInfo existing = foundGroups.get(info.id);
        boolean isNew = existing == null;

        int number = existing != null ? existing.number : info.number;
        if (number <= 0) {
            number = nextGroupNumber++;
        } else {
            nextGroupNumber = Math.max(nextGroupNumber, number + 1);
        }

        GroupInfo updated = new GroupInfo(
                number,
                info.id,
                info.title,
                info.link,
                info.memberCount,
                info.status,
                false,
                true
        );
        if (sameGroup(existing, updated)) return false;
        foundGroups.put(info.id, updated);

        schedulePersistDiscovery();
        listener.onFoundGroupsChanged();
        return isNew;
    }

    private static boolean sameGroup(GroupInfo first, GroupInfo second) {
        return first != null
                && first.number == second.number
                && first.id == second.id
                && first.title.equals(second.title)
                && first.link.equals(second.link)
                && first.memberCount == second.memberCount
                && first.status.equals(second.status)
                && first.canSend == second.canSend
                && first.canSendPhotos == second.canSendPhotos
                && first.discoveredBySearch == second.discoveredBySearch;
    }

    private void observeDirectSender(TdApi.Message message) {
        TdApi.Chat chat = chatCache.get(message.chatId);
        if (chat == null || !(chat.type instanceof TdApi.ChatTypePrivate)) {
            return;
        }

        Object sender = readObjectField(message, "senderId");
        long userId = readLongField(sender, "userId", 0L);
        if (userId == 0L) return;

        directSenderIds.add(userId);
        TdApi.User user = userCache.get(userId);
        if (user != null) {
            storeDirectUserIfPhoneVisible(user);
        }
    }

    private boolean storeDirectUserIfPhoneVisible(TdApi.User user) {
        String phone = "";
        try {
            Field field = user.getClass().getField("phoneNumber");
            Object value = field.get(user);
            if (value instanceof String) {
                phone = ((String) value).trim();
            }
        } catch (Throwable ignored) {
        }

        if (phone.isEmpty()) {
            return false;
        }

        String name = ((user.firstName == null ? "" : user.firstName) + " "
                + (user.lastName == null ? "" : user.lastName)).trim();

        ContactInfo existing = observedUsers.get(user.id);
        boolean isNew = existing == null;

        int number = existing == null ? 0 : existing.number;
        if (number <= 0) {
            number = nextContactNumber++;
        } else {
            nextContactNumber = Math.max(nextContactNumber, number + 1);
        }

        ContactInfo updated = new ContactInfo(number, user.id, name, phone);
        if (existing != null
                && existing.name.equals(updated.name)
                && existing.phone.equals(updated.phone)) {
            return false;
        }

        observedUsers.put(user.id, updated);
        schedulePersistDiscovery();
        listener.onObservedUsersChanged();
        return isNew;
    }

    private static Object readObjectField(Object source, String fieldName) {
        if (source == null) return null;
        try {
            return source.getClass().getField(fieldName).get(source);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int readIntField(Object source, String fieldName, int fallback) {
        Object value = readObjectField(source, fieldName);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static long readLongField(Object source, String fieldName, long fallback) {
        Object value = readObjectField(source, fieldName);
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    private static String extractPublicUsername(Object source) {
        Object direct = readObjectField(source, "username");
        if (direct instanceof String && !((String) direct).trim().isEmpty()) {
            return ((String) direct).trim().replaceFirst("^@", "");
        }

        Object usernames = readObjectField(source, "usernames");
        if (usernames == null) return "";

        Object active = readObjectField(usernames, "activeUsernames");
        if (active != null && active.getClass().isArray() && Array.getLength(active) > 0) {
            Object first = Array.get(active, 0);
            if (first instanceof String) return ((String) first).trim().replaceFirst("^@", "");
        }

        Object editable = readObjectField(usernames, "editableUsername");
        if (editable instanceof String) {
            return ((String) editable).trim().replaceFirst("^@", "");
        }
        return "";
    }

    private static boolean isJoinedGroupStatus(Object status) {
        if (status instanceof TdApi.ChatMemberStatusCreator) {
            return ((TdApi.ChatMemberStatusCreator) status).isMember;
        }
        if (status instanceof TdApi.ChatMemberStatusRestricted) {
            return ((TdApi.ChatMemberStatusRestricted) status).isMember;
        }
        return status instanceof TdApi.ChatMemberStatusAdministrator
                || status instanceof TdApi.ChatMemberStatusMember;
    }

    private static boolean isPrivilegedGroupMember(Object status) {
        return status instanceof TdApi.ChatMemberStatusAdministrator
                || (status instanceof TdApi.ChatMemberStatusCreator
                    && ((TdApi.ChatMemberStatusCreator) status).isMember);
    }

    private static boolean chatAllowsMessages(TdApi.Chat chat, Object status, boolean photo) {
        if (!isJoinedGroupStatus(status)) return false;
        if (isPrivilegedGroupMember(status)) return true;
        if (chat == null || chat.permissions == null) return false;
        boolean groupAllows = photo ? chat.permissions.canSendPhotos : chat.permissions.canSendBasicMessages;
        if (!groupAllows) return false;
        if (status instanceof TdApi.ChatMemberStatusRestricted) {
            TdApi.ChatPermissions personal = ((TdApi.ChatMemberStatusRestricted) status).permissions;
            return personal != null && (photo ? personal.canSendPhotos : personal.canSendBasicMessages);
        }
        return status instanceof TdApi.ChatMemberStatusMember;
    }

    private static String describeMemberStatus(Object status) {
        if (status == null) return "وضعیت عضویت نامشخص";
        if (status instanceof TdApi.ChatMemberStatusCreator) {
            return isJoinedGroupStatus(status) ? "مالک گروه ✅" : "مالک گروه — عضو نیست";
        }
        if (status instanceof TdApi.ChatMemberStatusAdministrator) return "مدیر گروه ✅";
        if (status instanceof TdApi.ChatMemberStatusMember) return "عضو گروه ✅";
        if (status instanceof TdApi.ChatMemberStatusRestricted) {
            return isJoinedGroupStatus(status) ? "عضو محدود ⚠️" : "محدود — عضو نیست";
        }
        if (status instanceof TdApi.ChatMemberStatusBanned) return "مسدود ⛔";
        if (status instanceof TdApi.ChatMemberStatusLeft) return "عضو نیست";
        return "وضعیت نامشخص";
    }

    private static boolean isGroupChat(TdApi.Chat chat) {
        if (chat == null) return false;
        if (chat.type instanceof TdApi.ChatTypeBasicGroup) return true;
        if (chat.type instanceof TdApi.ChatTypeSupergroup) {
            try {
                Field field = chat.type.getClass().getField("isChannel");
                Object value = field.get(chat.type);
                return !(value instanceof Boolean) || !((Boolean) value);
            } catch (Throwable ignored) {
                return true;
            }
        }
        return false;
    }

    private void handleConnectionState(TdApi.ConnectionState state) {
        String message;
        boolean ready = false;

        if (state instanceof TdApi.ConnectionStateReady) {
            message = "تلگرام: متصل ✅";
            ready = true;
        } else if (state instanceof TdApi.ConnectionStateConnectingToProxy) {
            message = "تلگرام: در حال اتصال به پروکسی...";
        } else if (state instanceof TdApi.ConnectionStateConnecting) {
            message = "تلگرام: در حال اتصال...";
        } else if (state instanceof TdApi.ConnectionStateUpdating) {
            message = "تلگرام: متصل است، در حال همگام‌سازی...";
        } else if (state instanceof TdApi.ConnectionStateWaitingForNetwork) {
            message = "تلگرام: منتظر اینترنت ⛔";
        } else {
            message = "تلگرام: وضعیت اتصال نامشخص";
        }

        connectionStatusMessage = message;
        connectionReady = ready;
        listener.onConnectionStatus(message, ready);
    }

    private void handleAuthorizationState(TdApi.AuthorizationState state) {
        if (state instanceof TdApi.AuthorizationStateWaitTdlibParameters) {
            currentStep = AuthStep.WAIT_PARAMETERS;
            listener.onAuthStep(currentStep, "در حال ارسال تنظیمات TDLib...");
            sendParameters();
        } else if (state instanceof TdApi.AuthorizationStateWaitPhoneNumber) {
            currentStep = AuthStep.PHONE;
            listener.onAuthStep(currentStep, "شماره تلفن را با کد کشور وارد کنید.");
        } else if (state instanceof TdApi.AuthorizationStateWaitCode) {
            currentStep = AuthStep.CODE;
            listener.onAuthStep(currentStep, "کد ورود تلگرام را وارد کنید.");
        } else if (state instanceof TdApi.AuthorizationStateWaitPassword) {
            currentStep = AuthStep.PASSWORD;
            listener.onAuthStep(currentStep, "رمز دومرحله‌ای تلگرام را وارد کنید.");
        } else if (state instanceof TdApi.AuthorizationStateWaitEmailAddress) {
            currentStep = AuthStep.EMAIL_ADDRESS;
            listener.onAuthStep(currentStep, "تلگرام آدرس ایمیل می‌خواهد.");
        } else if (state instanceof TdApi.AuthorizationStateWaitEmailCode) {
            currentStep = AuthStep.EMAIL_CODE;
            listener.onAuthStep(currentStep, "کد ارسال‌شده به ایمیل را وارد کنید.");
        } else if (state instanceof TdApi.AuthorizationStateWaitOtherDeviceConfirmation) {
            currentStep = AuthStep.OTHER_DEVICE;
            String link = ((TdApi.AuthorizationStateWaitOtherDeviceConfirmation) state).link;
            listener.onAuthStep(currentStep, "ورود را در دستگاه دیگر تأیید کنید: " + link);
        } else if (state instanceof TdApi.AuthorizationStateWaitRegistration) {
            currentStep = AuthStep.REGISTRATION;
            listener.onAuthStep(currentStep, "این شماره نیاز به ثبت‌نام حساب جدید دارد؛ نسخه فعلی برای ورود حساب موجود ساخته شده است.");
        } else if (state instanceof TdApi.AuthorizationStateReady) {
            currentStep = AuthStep.READY;
            listener.onAuthStep(currentStep, "متصل شد. بانک واژه می‌تواند از پیام‌های مرتبط یاد بگیرد.");
            refreshTargetGroups();
        } else if (state instanceof TdApi.AuthorizationStateLoggingOut
                || state instanceof TdApi.AuthorizationStateClosing) {
            currentStep = AuthStep.LOGGING_OUT;
            listener.onAuthStep(currentStep, "در حال بستن نشست تلگرام...");
        } else if (state instanceof TdApi.AuthorizationStateClosed) {
            currentStep = AuthStep.CLOSED;
            listener.onAuthStep(currentStep, "اتصال تلگرام بسته شد.");
        }
    }

    private void sendParameters() {
        TdApi.SetTdlibParameters request = new TdApi.SetTdlibParameters();

        File databaseDir = new File(context.getFilesDir(), "tdlib");
        File filesDir = new File(databaseDir, "files");
        databaseDir.mkdirs();
        filesDir.mkdirs();

        request.useTestDc = false;
        request.databaseDirectory = databaseDir.getAbsolutePath();
        request.filesDirectory = filesDir.getAbsolutePath();
        request.databaseEncryptionKey = new byte[0];
        request.useFileDatabase = true;
        request.useChatInfoDatabase = true;
        request.useMessageDatabase = true;
        request.useSecretChats = false;
        request.apiId = apiId;
        request.apiHash = apiHash;
        request.systemLanguageCode = "fa";
        request.deviceModel = Build.MODEL == null ? "Android" : Build.MODEL;
        request.systemVersion = Build.VERSION.RELEASE == null ? "Android" : Build.VERSION.RELEASE;
        request.applicationVersion = "1.12.0";

        sendAuth(request);
    }

    private void sendAuth(TdApi.Function function) {
        Client local = client;
        if (local == null) {
            listener.onError("کلاینت تلگرام فعال نیست.");
            return;
        }

        local.send(function, result -> {
            if (result instanceof TdApi.Error) {
                TdApi.Error error = (TdApi.Error) result;
                listener.onError("Telegram " + error.code + ": " + error.message);
            }
        });
    }

    public synchronized void close() {
        Client local = client;
        client = null;
        currentStep = AuthStep.IDLE;
        connectionReady = false;
        connectionStatusMessage = "تلگرام: اتصال بسته است.";
        listener.onConnectionStatus(connectionStatusMessage, false);
        apiHash = "";

        if (local != null) {
            try {
                local.send(new TdApi.Close(), result -> { });
            } catch (Throwable ignored) {
            }
        }
    }

    private static String cleanLabel(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }

    private synchronized void loadDiscovery() {
        boolean migrated = false;

        try {
            JSONArray groups = new JSONArray(discoveryPrefs.getString(KEY_GROUPS_JSON, "[]"));
            for (int i = 0; i < groups.length(); i++) {
                JSONObject item = groups.optJSONObject(i);
                if (item == null) continue;
                if (!item.optBoolean("discoveredBySearch", false)) continue;

                long id = item.optLong("id", 0L);
                if (id == 0L) continue;

                int number = item.optInt("number", 0);
                if (number <= 0) {
                    number = nextGroupNumber++;
                    migrated = true;
                } else {
                    nextGroupNumber = Math.max(nextGroupNumber, number + 1);
                }

                foundGroups.put(id, new GroupInfo(
                        number,
                        id,
                        item.optString("title", ""),
                        item.optString("link", ""),
                        item.optInt("memberCount", 0),
                        item.optString("status", "نتیجه جستجوی بانک کلمات"),
                        false,
                        true
                ));
            }

            JSONArray users = new JSONArray(
                    discoveryPrefs.getString(KEY_OBSERVED_USERS_JSON, "[]")
            );
            for (int i = 0; i < users.length(); i++) {
                JSONObject item = users.optJSONObject(i);
                if (item == null) continue;

                long id = item.optLong("id", 0L);
                String phone = item.optString("phone", "").trim();
                if (id == 0L || phone.isEmpty()) continue;

                int number = item.optInt("number", 0);
                if (number <= 0) {
                    number = nextContactNumber++;
                    migrated = true;
                } else {
                    nextContactNumber = Math.max(nextContactNumber, number + 1);
                }

                observedUsers.put(id, new ContactInfo(
                        number,
                        id,
                        item.optString("name", ""),
                        phone
                ));
            }

            if (discoveryPrefs.contains(LEGACY_CONTACTS_JSON)) {
                discoveryPrefs.edit().remove(LEGACY_CONTACTS_JSON).apply();
            }

            if (migrated) {
                schedulePersistDiscovery();
            }
        } catch (Throwable ignored) {
        }
    }

    private void schedulePersistDiscovery() {
        persistDirty.set(true);

        if (!persistRunning.compareAndSet(false, true)) {
            return;
        }

        storageExecutor.execute(() -> {
            try {
                do {
                    persistDirty.set(false);
                    writeDiscoverySnapshot();
                } while (persistDirty.get());
            } finally {
                persistRunning.set(false);
                if (persistDirty.get()) {
                    schedulePersistDiscovery();
                }
            }
        });
    }

    private void writeDiscoverySnapshot() {
        try {
            JSONArray groups = new JSONArray();
            for (GroupInfo info : foundGroups.values()) {
                JSONObject item = new JSONObject();
                item.put("number", info.number);
                item.put("id", info.id);
                item.put("title", info.title);
                item.put("link", info.link);
                item.put("memberCount", info.memberCount);
                item.put("status", info.status);
                item.put("canSend", false);
                item.put("discoveredBySearch", true);
                groups.put(item);
            }

            JSONArray users = new JSONArray();
            for (ContactInfo info : observedUsers.values()) {
                if (info.phone == null || info.phone.trim().isEmpty()) continue;

                JSONObject item = new JSONObject();
                item.put("number", info.number);
                item.put("id", info.id);
                item.put("name", info.name);
                item.put("phone", info.phone);
                users.put(item);
            }

            discoveryPrefs.edit()
                    .putString(KEY_GROUPS_JSON, groups.toString())
                    .putString(KEY_OBSERVED_USERS_JSON, users.toString())
                    .remove(LEGACY_CONTACTS_JSON)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    private static String safeMessage(Throwable error) {
        if (error == null) return "نامشخص";
        String value = error.getMessage();
        return value == null || value.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : value;
    }
}
