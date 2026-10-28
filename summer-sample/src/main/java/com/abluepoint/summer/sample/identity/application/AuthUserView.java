package com.abluepoint.summer.sample.identity.application;

import com.abluepoint.summer.sample.identity.domain.AuthAccount;



public record AuthUserView(String username, String role, boolean active) {
    public static AuthUserView from(AuthAccount user) { return new AuthUserView(user.username(), user.role(), user.active()); }
}
