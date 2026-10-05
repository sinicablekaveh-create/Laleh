package com.sinicable.telegramelectric;

import android.net.Uri;

final class ProxyLinkParser {
    enum Type { MTPROTO, SOCKS5 }

    static final class ProxyConfig {
        final Type type;
        final String server;
        final int port;
        final String secret;
        final String username;
        final String password;

        ProxyConfig(Type type, String server, int port, String secret, String username, String password) {
            this.type = type;
            this.server = server;
            this.port = port;
            this.secret = secret == null ? "" : secret;
            this.username = username == null ? "" : username;
            this.password = password == null ? "" : password;
        }
    }

    static ProxyConfig parse(String raw) {
        if (raw == null) throw new IllegalArgumentException("لینک پروکسی خالی است.");

        String value = raw.trim().replace("&amp;", "&");
        if (value.isEmpty()) throw new IllegalArgumentException("لینک پروکسی خالی است.");

        if (value.startsWith("t.me/") || value.startsWith("telegram.me/")) {
            value = "https://" + value;
        }

        Uri uri = Uri.parse(value);
        String scheme = safe(uri.getScheme()).toLowerCase();
        String host = safe(uri.getHost()).toLowerCase();
        String path = safe(uri.getPath()).toLowerCase();

        boolean isMtproto =
                ("tg".equals(scheme) && "proxy".equals(host))
                        || (("http".equals(scheme) || "https".equals(scheme))
                        && ("t.me".equals(host) || "telegram.me".equals(host))
                        && path.startsWith("/proxy"));

        boolean isSocks =
                ("tg".equals(scheme) && "socks".equals(host))
                        || (("http".equals(scheme) || "https".equals(scheme))
                        && ("t.me".equals(host) || "telegram.me".equals(host))
                        && path.startsWith("/socks"));

        if (!isMtproto && !isSocks) {
            throw new IllegalArgumentException("فرمت لینک پشتیبانی نمی‌شود. لینک MTProto یا SOCKS5 تلگرام را پیست کنید.");
        }

        String server = safe(uri.getQueryParameter("server")).trim();
        String portText = safe(uri.getQueryParameter("port")).trim();

        if (server.isEmpty()) throw new IllegalArgumentException("آدرس server در لینک پیدا نشد.");

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("پورت پروکسی معتبر نیست.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("پورت پروکسی باید بین 1 تا 65535 باشد.");
        }

        if (isMtproto) {
            String secret = safe(uri.getQueryParameter("secret")).trim();
            if (secret.isEmpty()) throw new IllegalArgumentException("Secret پروکسی MTProto پیدا نشد.");
            return new ProxyConfig(Type.MTPROTO, server, port, secret, "", "");
        }

        String user = firstNonEmpty(uri.getQueryParameter("user"), uri.getQueryParameter("username"));
        String pass = firstNonEmpty(uri.getQueryParameter("pass"), uri.getQueryParameter("password"));
        return new ProxyConfig(Type.SOCKS5, server, port, "", user, pass);
    }

    private static String firstNonEmpty(String first, String second) {
        String a = safe(first);
        return a.isEmpty() ? safe(second) : a;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private ProxyLinkParser() {}
}
