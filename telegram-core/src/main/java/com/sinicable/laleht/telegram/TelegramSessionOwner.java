package com.sinicable.laleht.telegram;

/**
 * Boundary contract for the sole component that owns the Telegram client lifecycle.
 *
 * <p>This module deliberately does not create a TDLib client. During the incremental
 * migration, {@code TelegramClientManager} in the app module remains the only session
 * owner. Future integrations must depend on this boundary instead of creating another
 * client or copying another application's session data.</p>
 */
public interface TelegramSessionOwner {
    boolean isAuthenticated();

    boolean isConnected();
}
