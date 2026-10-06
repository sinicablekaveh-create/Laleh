package com.sinicable.telegramelectric.groupsearch;

public class GroupFilter {

    public boolean isAllowedGroup(boolean isBasicGroup, boolean isSupergroup, boolean isChannel) {
        if (isChannel) {
            return false;
        }

        return isBasicGroup || isSupergroup;
    }
}
