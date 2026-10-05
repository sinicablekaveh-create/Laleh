package com.sinicable.telegramelectric;

import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

final class PhotoMessageStore {
    private static final String PREFIX = "auto_message_photo.";

    static String copyIntoApp(Context context, Uri uri) throws Exception {
        if (uri == null) throw new IllegalArgumentException("عکس انتخاب نشده است.");

        String mime = context.getContentResolver().getType(uri);
        String extension = mime == null
                ? null
                : MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);

        if (extension == null || extension.trim().isEmpty()) {
            extension = "jpg";
        }

        clear(context, null);

        File output = new File(context.getFilesDir(), PREFIX + extension);
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             FileOutputStream fileOutput = new FileOutputStream(output)) {
            if (input == null) {
                throw new IllegalStateException("فایل عکس باز نشد.");
            }

            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                fileOutput.write(buffer, 0, read);
            }
            fileOutput.flush();
        }

        if (!output.exists() || output.length() == 0L) {
            throw new IllegalStateException("ذخیره عکس ناموفق بود.");
        }

        return output.getAbsolutePath();
    }

    static void clear(Context context, String keepPath) {
        File[] files = context.getFilesDir().listFiles();
        if (files == null) return;

        for (File file : files) {
            if (!file.getName().startsWith(PREFIX)) continue;
            if (keepPath != null && keepPath.equals(file.getAbsolutePath())) continue;
            try {
                file.delete();
            } catch (Throwable ignored) {
            }
        }
    }

    static boolean exists(String path) {
        return path != null && !path.trim().isEmpty() && new File(path).isFile();
    }

    private PhotoMessageStore() {
    }
}
