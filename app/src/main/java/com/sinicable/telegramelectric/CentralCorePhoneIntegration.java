package com.sinicable.telegramelectric;

/**
 * Integration bridge between chat phone search results and the main Laleh search flow.
 * Keeps TDLib synchronization and UI components separated.
 */
public final class CentralCorePhoneIntegration {

    private CentralCorePhoneIntegration() {
    }

    public static String normalizePhoneResult(String phone) {
        if (phone == null) {
            return "";
        }
        return phone.replaceAll("[^0-9+]", "");
    }

    public static boolean isValidPhoneResult(String phone) {
        String value = normalizePhoneResult(phone);
        return value.length() >= 7;
    }
}
