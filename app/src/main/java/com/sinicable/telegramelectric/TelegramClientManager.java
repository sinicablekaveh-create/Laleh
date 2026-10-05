package com.sinicable.telegramelectric;

import android.content.Context;
import android.os.Build;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import java.io.File;

public final class TelegramClientManager {
    public enum AuthStep {
        IDLE, WAIT_PARAMETERS, PHONE, CODE, PASSWORD, EMAIL_ADDRESS, EMAIL_CODE,
        OTHER_DEVICE, REGISTRATION, READY, LOGGING_OUT, CLOSED
    }

    public interface Listener {
        void onAuthStep(AuthStep step, String message);
        void onError(String message);
        void onMessageText(String text);
    }

    private final Context context;
    private final Listener listener;
    private volatile AuthStep currentStep = AuthStep.IDLE;
    private Client client;
    private int apiId;
    private String apiHash = "";

    public TelegramClientManager(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public synchronized void start(int apiId, String apiHash) {
        if (apiId <= 0 || apiHash == null || apiHash.trim().isEmpty()) {
            listener.onError("API ID و API Hash معتبر وارد کنید.");
            return;
        }

        close();
        this.apiId = apiId;
        this.apiHash = apiHash.trim();

        try {
            System.loadLibrary("tdjni");
            Client.execute(new TdApi.SetLogVerbosityLevel(1));
            client = Client.create(
                    this::onUpdate,
                    error -> listener.onError("خطای TDLib: " + safeMessage(error)),
                    error -> listener.onError("خطای TDLib: " + safeMessage(error))
            );
            listener.onAuthStep(AuthStep.WAIT_PARAMETERS, "در حال راه‌اندازی اتصال تلگرام...");
        } catch (Throwable error) {
            client = null;
            listener.onError("راه‌اندازی TDLib ناموفق بود: " + safeMessage(error));
        }
    }

    public AuthStep getCurrentStep() {
        return currentStep;
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

    private void onUpdate(TdApi.Object object) {
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
        request.applicationVersion = "1.0.0";

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
        apiHash = "";

        if (local != null) {
            try {
                local.send(new TdApi.Close(), result -> { });
            } catch (Throwable ignored) {
            }
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
