package com.sinicable.telegramelectric;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public final class BackgroundCoreService extends Service {
    private static final String CHANNEL_ID = "telegram_electric_background";
    private static final int NOTIFICATION_ID = 1601;

    public static void start(Context context) {
        Intent intent = new Intent(context, BackgroundCoreService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, BackgroundCoreService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        BackgroundModeStore store = new BackgroundModeStore(this);
        if (!store.isEnabled()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(
                NOTIFICATION_ID,
                buildNotification("هسته مرکزی در پس‌زمینه آماده اجرا است.")
        );

        ensureRuntime();
        return START_STICKY;
    }

    private void ensureRuntime() {
        BackgroundRuntime.Snapshot existing = BackgroundRuntime.get();
        if (existing != null) {
            return;
        }

        WordBank wordBank = new WordBank(this);

        TelegramClientManager telegram = new TelegramClientManager(
                this,
                new TelegramClientManager.Listener() {
                    @Override
                    public void onAuthStep(TelegramClientManager.AuthStep step, String message) {
                        updateNotification(message);
                    }

                    @Override
                    public void onProxyStatus(String message) {
                    }

                    @Override
                    public void onConnectionStatus(String message, boolean ready) {
                        updateNotification(message);
                    }

                    @Override
                    public void onTargetGroupChanged(long chatId) {
                    }

                    @Override
                    public void onFoundGroupsChanged() {
                    }

                    @Override
                    public void onObservedUsersChanged() {
                    }

                    @Override
                    public void onError(String message) {
                        updateNotification("خطا: " + message);
                    }

                    @Override
                    public void onMessageText(String text) {
                        wordBank.learnFromMessage(text);
                    }
                }
        );

        CentralCore core = new CentralCore(
                this,
                telegram,
                wordBank,
                new CentralCore.Listener() {
                    @Override
                    public void onStatus(String message) {
                        updateNotification(message);
                    }

                    @Override
                    public void onDataChanged() {
                    }
                }
        );

        BackgroundRuntime.attach(telegram, wordBank, core);

        AuthSessionStore auth = new AuthSessionStore(this);
        if (auth.hasCredentials()) {
            telegram.start(auth.getApiId(), auth.getApiHash());
        } else {
            updateNotification("برای اجرای پس‌زمینه ابتدا یک‌بار وارد تلگرام شو.");
        }
    }

    private void updateNotification(String text) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    private Notification buildNotification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("Telegram Electric")
                .setContentText(text == null ? "اجرا در پس‌زمینه فعال است." : text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "اجرای پس‌زمینه Telegram Electric",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("برای فعال نگه داشتن هسته مرکزی در پس‌زمینه");
        manager.createNotificationChannel(channel);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
