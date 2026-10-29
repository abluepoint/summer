package com.abluepoint.summer.sample.authorization.application.command;

import java.util.List;

/** Use-case input implemented by the HTTP request without duplicating its state. */
public interface OAuthClientCreateCommand {
    String clientId();
    String clientName();
    Boolean publicClient();
    List<String> redirectUris();
    List<String> scopes();
}
