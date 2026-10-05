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
import java.util.concurrent.atomic.AtomicInteger;

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
    private Client client;
    private int apiId;
    private String apiHash = "";
    private volatile ProxyLinkParser.ProxyConfig pendingProxy;
    private final Map<Long, GroupInfo> targetGroups = new ConcurrentHashMap<>();
    private final Map<Long, GroupInfo> foundGroups = new ConcurrentHashMap<>();
    private final Map<Long, ContactInfo> observedUsers = new ConcurrentHashMap<>();
    private final Map<Long, TdApi.User> userCache = new ConcurrentHashMap<>();
    private final Map<Long, TdApi.Chat> chatCache = new ConcurrentHashMap<>();
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


    public static final class GroupInfo {
        public final int number;
        public final long id;
        public final String title;
        public final String link;
        public final int memberCount;
        public final String status;
        public final boolean canSend;
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
            this.number = Math.max(0, number);
            this.id = id;
            this.title = cleanLabel(title, String.valueOf(id));
            this.link = cleanLabel(link, "لینک عمومی در دسترس نیست");
            this.memberCount = Math.max(0, memberCount);
            this.status = cleanLabel(status, "وضعیت نامشخص");
            this.canSend = canSend;
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

            setField(photoContent, "photo", inputFile);

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
            if (hasField(inputMessagePhotoClass, "width")) {
                setField(photoContent, "width", 0);
            }
            if (hasField(inputMessagePhotoClass, "height")) {
                setField(photoContent, "height", 0);
            }
            if (hasField(inputMessagePhotoClass, "ttl")) {
                setField(photoContent, "ttl", 0);
            }
            if (hasField(inputMessagePhotoClass, "addedStickerFileIds")) {
                setField(photoContent, "addedStickerFileIds", new int[0]);
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

    public void sendTextToUser(long userId, String message, SendCallback callback) {
        Client local = client;
        if (local == null || currentStep != AuthStep.READY) {
            if (callback != null) callback.onResult(false, "تلگرام آماده ارسال نیست.");
            return;
        }

        local.send(new TdApi.CreatePrivateChat(userId, false), result -> {
            if (result instanceof TdApi.Error) {
                TdApi.Error error = (TdApi.Error) result;
                if (callback != null) callback.onResult(false, error.code + ": " + error.message);
                return;
            }

            if (result instanceof TdApi.Chat) {
                sendTextToChat(((TdApi.Chat) result).id, message, callback);
            } else {
                if (callback != null) callback.onResult(false, "چت خصوصی ساخته نشد.");
            }
        });
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

            AtomicInteger remaining = new AtomicInteger(ids.length);
            AtomicInteger newItems = new AtomicInteger(0);
            AtomicInteger validItems = new AtomicInteger(0);

            for (long chatId : ids) {
                local.send(new TdApi.GetChat(chatId), chatResult -> {
                    if (chatResult instanceof TdApi.Chat && isGroupChat((TdApi.Chat) chatResult)) {
                        TdApi.Chat chat = (TdApi.Chat) chatResult;
                        validItems.incrementAndGet();
                        boolean isNew = !foundGroups.containsKey(chat.id);
                        captureKnownGroup(chat, true);
                        if (isNew) newItems.incrementAndGet();
                    }

                    if (remaining.decrementAndGet() == 0 && callback != null) {
                        callback.onResult(
                                true,
                                newItems.get(),
                                validItems.get(),
                                "جستجوی عمومی برای نمایش کامل شد."
                        );
                    }
                });
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

            AtomicInteger remaining = new AtomicInteger(ids.length);
            AtomicInteger newItems = new AtomicInteger(0);
            AtomicInteger validItems = new AtomicInteger(0);

            for (long chatId : ids) {
                local.send(new TdApi.GetChat(chatId), chatResult -> {
                    if (chatResult instanceof TdApi.Chat && isGroupChat((TdApi.Chat) chatResult)) {
                        TdApi.Chat chat = (TdApi.Chat) chatResult;
                        validItems.incrementAndGet();
                        boolean isNew = !foundGroups.containsKey(chat.id);
                        captureKnownGroup(chat, false);
                        if (isNew) newItems.incrementAndGet();
                    }

                    if (remaining.decrementAndGet() == 0 && callback != null) {
                        callback.onResult(
                                true,
                                newItems.get(),
                                validItems.get(),
                                "جستجوی گروه‌های موجود در حساب کامل شد."
                        );
                    }
                });
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
            if (chat != null && isGroupChat(chat)) {
                captureKnownGroup(chat, false);
            }
            return;
        }

        if (object instanceof TdApi.UpdateChatTitle) {
            TdApi.UpdateChatTitle update = (TdApi.UpdateChatTitle) object;
            GroupInfo existing = foundGroups.get(update.chatId);
            if (existing != null) {
                foundGroups.put(update.chatId, new GroupInfo(
                        existing.number,
                        existing.id,
                        update.title,
                        existing.link,
                        existing.memberCount,
                        existing.status,
                        existing.canSend,
                        existing.discoveredBySearch
                ));
                persistDiscovery();
                listener.onRecipientsChanged();
            }
            return;
        }

        if (object instanceof TdApi.UpdateUser) {
            TdApi.User user = ((TdApi.UpdateUser) object).user;
            if (user != null && isContactUser(user)) {
                storeContact(user);
            }
            return;
        }

        if (object instanceof TdApi.UpdateAuthorizationState) {
            handleAuthorizationState(((TdApi.UpdateAuthorizationState) object).authorizationState);
            return;
        }

        if (object instanceof TdApi.UpdateNewMessage) {
            TdApi.Message message = ((TdApi.UpdateNewMessage) object).message;
            if (message != null && message.content instanceof TdApi.MessageText) {
                TdApi.MessageText content = (TdApi.MessageText) message.content;
                if (content.text != null && content.text.text != null && !content.text.text.isEmpty()) {
                    listener.onMessageText(content.text.text);
                }
            }
        }
    }



    private void captureKnownGroup(TdApi.Chat chat, boolean discoveredBySearch) {
        if (chat == null || !isGroupChat(chat)) return;

        GroupInfo existing = foundGroups.get(chat.id);
        GroupInfo base = new GroupInfo(
                chat.id,
                chat.title,
                existing == null ? "" : existing.link,
                existing == null ? 0 : existing.memberCount,
                existing == null ? "موجود در حساب تلگرام" : existing.status,
                existing != null && existing.canSend,
                discoveredBySearch || (existing != null && existing.discoveredBySearch)
        );
        storeGroup(base);

        Client local = client;
        if (local == null) return;

        if (chat.type instanceof TdApi.ChatTypeSupergroup) {
            long supergroupId = ((TdApi.ChatTypeSupergroup) chat.type).supergroupId;
            local.send(new TdApi.GetSupergroup(supergroupId), result -> {
                if (result instanceof TdApi.Supergroup) {
                    updateGroupFromMeta(chat, result);
                }
            });
        } else if (chat.type instanceof TdApi.ChatTypeBasicGroup) {
            long basicGroupId = ((TdApi.ChatTypeBasicGroup) chat.type).basicGroupId;
            local.send(new TdApi.GetBasicGroup(basicGroupId), result -> {
                if (result instanceof TdApi.BasicGroup) {
                    updateGroupFromMeta(chat, result);
                }
            });
        }
    }

    private void updateGroupFromMeta(TdApi.Chat chat, Object meta) {
        GroupInfo existing = foundGroups.get(chat.id);
        int memberCount = readIntField(meta, "memberCount", existing == null ? 0 : existing.memberCount);
        Object statusObject = readObjectField(meta, "status");
        String status = describeMemberStatus(statusObject);
        boolean canSend = canSendFromStatus(statusObject);

        String username = extractPublicUsername(meta);
        String link = username.isEmpty()
                ? (existing == null ? "" : existing.link)
                : "https://t.me/" + username;

        storeGroup(new GroupInfo(
                chat.id,
                chat.title,
                link,
                memberCount,
                status,
                canSend,
                existing != null && existing.discoveredBySearch
        ));
    }

    private synchronized boolean storeGroup(GroupInfo info) {
        GroupInfo existing = foundGroups.get(info.id);
        boolean isNew = existing == null;

        int number = existing != null ? existing.number : info.number;
        if (number <= 0) {
            number = nextGroupNumber++;
        } else {
            nextGroupNumber = Math.max(nextGroupNumber, number + 1);
        }

        GroupInfo stored = new GroupInfo(
                number,
                info.id,
                info.title,
                info.link,
                info.memberCount,
                info.status,
                info.canSend,
                info.discoveredBySearch || (existing != null && existing.discoveredBySearch)
        );

        foundGroups.put(info.id, stored);
        persistDiscovery();
        listener.onRecipientsChanged();
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

    private static boolean canSendFromStatus(Object status) {
        if (status == null) return false;
        String name = status.getClass().getSimpleName();

        if (name.contains("Creator")
                || name.contains("Administrator")
                || name.contains("Member")) {
            return true;
        }

        if (name.contains("Restricted")) {
            Object permissions = readObjectField(status, "permissions");
            Object basic = readObjectField(permissions, "canSendBasicMessages");
            if (basic instanceof Boolean) return (Boolean) basic;

            Object legacy = readObjectField(permissions, "canSendMessages");
            if (legacy instanceof Boolean) return (Boolean) legacy;
        }

        return false;
    }

    private static String describeMemberStatus(Object status) {
        if (status == null) return "وضعیت عضویت نامشخص";
        String name = status.getClass().getSimpleName();
        if (name.contains("Creator")) return "مالک گروه ✅";
        if (name.contains("Administrator")) return "مدیر گروه ✅";
        if (name.contains("Member")) return "عضو گروه ✅";
        if (name.contains("Restricted")) return "عضو محدود ⚠️";
        if (name.contains("Banned")) return "مسدود ⛔";
        if (name.contains("Left")) return "عضو نیست";
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

    private boolean storeContact(TdApi.User user) {
        String name = ((user.firstName == null ? "" : user.firstName) + " "
                + (user.lastName == null ? "" : user.lastName)).trim();
        String phone = "";
        try {
            Field field = user.getClass().getField("phoneNumber");
            Object value = field.get(user);
            if (value instanceof String) phone = (String) value;
        } catch (Throwable ignored) {
        }

        ContactInfo existing = telegramContacts.get(user.id);
        boolean isNew = existing == null;

        int number = existing == null ? 0 : existing.number;
        if (number <= 0) {
            number = nextContactNumber++;
        } else {
            nextContactNumber = Math.max(nextContactNumber, number + 1);
        }

        telegramContacts.put(user.id, new ContactInfo(number, user.id, name, phone));
        persistDiscovery();
        listener.onRecipientsChanged();
        return isNew;
    }

    private static boolean isContactUser(TdApi.User user) {
        try {
            Field field = user.getClass().getField("isContact");
            Object value = field.get(user);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable ignored) {
            return false;
        }
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
        request.applicationVersion = "1.9.1";

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
                        item.optString("status", ""),
                        item.optBoolean("canSend", false),
                        item.optBoolean("discoveredBySearch", false)
                ));
            }

            JSONArray contacts = new JSONArray(discoveryPrefs.getString(KEY_CONTACTS_JSON, "[]"));
            for (int i = 0; i < contacts.length(); i++) {
                JSONObject item = contacts.optJSONObject(i);
                if (item == null) continue;
                long id = item.optLong("id", 0L);
                if (id == 0L) continue;

                int number = item.optInt("number", 0);
                if (number <= 0) {
                    number = nextContactNumber++;
                    migrated = true;
                } else {
                    nextContactNumber = Math.max(nextContactNumber, number + 1);
                }

                telegramContacts.put(id, new ContactInfo(
                        number,
                        id,
                        item.optString("name", ""),
                        item.optString("phone", "")
                ));
            }

            if (migrated) {
                persistDiscovery();
            }
        } catch (Throwable ignored) {
        }
    }

    private synchronized void persistDiscovery() {
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
                item.put("canSend", info.canSend);
                item.put("discoveredBySearch", info.discoveredBySearch);
                groups.put(item);
            }

            JSONArray contacts = new JSONArray();
            for (ContactInfo info : telegramContacts.values()) {
                JSONObject item = new JSONObject();
                item.put("number", info.number);
                item.put("id", info.id);
                item.put("name", info.name);
                item.put("phone", info.phone);
                contacts.put(item);
            }

            discoveryPrefs.edit()
                    .putString(KEY_GROUPS_JSON, groups.toString())
                    .putString(KEY_CONTACTS_JSON, contacts.toString())
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
