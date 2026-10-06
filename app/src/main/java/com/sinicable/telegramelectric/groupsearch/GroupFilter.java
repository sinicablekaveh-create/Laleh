package com.sinicable.telegramelectric.groupsearch;

import org.drinkless.tdlib.TdApi;

public class GroupFilter {

    public boolean isGroup(Object chatType) {
        if (chatType instanceof TdApi.ChatTypeBasicGroup) {
            return true;
        }
        if (chatType instanceof TdApi.ChatTypeSupergroup) {
            return !((TdApi.ChatTypeSupergroup) chatType).isChannel;
        }
        return false;
    }

    public boolean isAllowedGroup(boolean isBasicGroup, boolean isSupergroup, boolean isChannel) {
        if (isChannel) {
            return false;
        }
        return isBasicGroup || isSupergroup;
    }
}
