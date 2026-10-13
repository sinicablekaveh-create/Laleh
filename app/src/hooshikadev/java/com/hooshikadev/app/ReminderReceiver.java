package com.hooshikadev.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL = "send_queue";

    @Override public void onReceive(Context context, Intent ignored) {
        SendQueue.QueueItem item = new SendQueue(context).current();
        if (item == null) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "صف ارسال hooshikadev", NotificationManager.IMPORTANCE_HIGH));
        Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 10, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification notification = new android.app.Notification.Builder(context, CHANNEL)
                .setSmallIcon(com.hooshikadev.app.R.drawable.ic_launcher)
                .setContentTitle("نوبت ارسال پیام")
                .setContentText((item.name.isEmpty() ? item.phone : item.name) + " آماده است؛ برای بررسی و ارسال لمس کنید.")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build();
        manager.notify(1001, notification);
    }

    public static void schedule(Context context, long atMillis) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(context, 11, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.AlarmManager alarms = context.getSystemService(android.app.AlarmManager.class);
        alarms.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, Math.max(System.currentTimeMillis(), atMillis), pending);
    }

    public static void cancel(Context context) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(context, 11, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) context.getSystemService(android.app.AlarmManager.class).cancel(pending);
    }
}
