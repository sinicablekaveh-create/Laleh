package com.sinicable.telegramelectric;

/**
 * Adapter layer between TDLib message updates and Laleh phone indexing.
 * Keeps TDLib client implementation isolated from search indexing.
 */
public final class TDLibChatSyncAdapter {

    private final ChatPhoneSyncManager syncManager;

    public TDLibChatSyncAdapter(ChatPhoneSyncManager syncManager) {
        this.syncManager = syncManager;
    }

    /**
     * Called when a Telegram message text is received from TDLib.
     * chatId and messageId can be used later for source tracking.
     */
    public void onMessageText(long chatId, long messageId, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        syncManager.processMessage(text);
    }
}
