package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/**
 * Opens Telegram links in an installed Telegram application.
 * Does not create a new TDLib session and does not replace TelegramClientManager.
 */
public final class TelegramAppConnector {
    private TelegramAppConnector() {}

    public static List<String> getInstalledTelegramApps(Context context) {
        List<String> result = new ArrayList<>();
        PackageManager pm = context.getPackageManager();

        String[] packages = {
                "org.telegram.messenger",
                "org.thunderdog.challegram"
        };

        for (String pkg : packages) {
            try {
                pm.getApplicationInfo(pkg, 0);
                result.add(pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return result;
    }

    public static boolean openUsername(Context context, String username, String packageName) {
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("tg://resolve?domain=" + username));

        if (packageName != null && !packageName.isEmpty()) {
            intent.setPackage(packageName);
        }

        if (intent.resolveActivity(context.getPackageManager()) == null) {
            return false;
        }

        context.startActivity(intent);
        return true;
    }

    public static boolean openPhone(Context context, String phone, String packageName) {
        String normalizedPhone = TelegramPhoneTarget.normalize(phone);
        if (normalizedPhone.isEmpty()) {
            return false;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("tg://resolve?phone=" + normalizedPhone));

        if (packageName != null && !packageName.isEmpty()) {
            intent.setPackage(packageName);
        }

        if (intent.resolveActivity(context.getPackageManager()) == null) {
            return false;
        }

        context.startActivity(intent);
        return true;
    }
}
