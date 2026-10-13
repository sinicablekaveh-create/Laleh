package com.hooshikadev.app;

import java.util.Objects;

public final class ContactRecord {
    public final String name;
    public final String phone;
    public final boolean consent;
    public final int score;

    public ContactRecord(String name, String phone, boolean consent, int score) {
        this.name = name == null ? "" : name.trim();
        this.phone = phone == null ? "" : phone.trim();
        this.consent = consent;
        this.score = Math.max(0, Math.min(100, score));
    }

    @Override public boolean equals(Object other) {
        return other instanceof ContactRecord && phone.equals(((ContactRecord) other).phone);
    }

    @Override public int hashCode() { return Objects.hash(phone); }
}
