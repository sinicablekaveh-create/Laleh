package com.sinicable.telegramelectric.groupsearch;

import org.drinkless.tdlib.TdApi;

public final class GroupFilter {
    private GroupFilter() { }
    public static boolean accepts(TdApi.Chat chat) {
        return chat != null && (chat.type instanceof TdApi.ChatTypeBasicGroup
                || (chat.type instanceof TdApi.ChatTypeSupergroup
                && !((TdApi.ChatTypeSupergroup) chat.type).isChannel));
    }
}
