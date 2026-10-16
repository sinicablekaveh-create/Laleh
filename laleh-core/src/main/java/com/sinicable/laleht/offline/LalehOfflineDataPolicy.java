package com.sinicable.laleht.offline;

/**
 * Documents the persistence boundary for Laleh features while they are migrated
 * incrementally into Laleh+T.
 */
public final class LalehOfflineDataPolicy {
    public static final String POLICY =
            "Keep Laleh offline data private to the app; do not copy Telegram sessions or credentials.";

    private LalehOfflineDataPolicy() {
    }
}
