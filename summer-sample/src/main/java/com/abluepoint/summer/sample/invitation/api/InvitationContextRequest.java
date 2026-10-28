package com.abluepoint.summer.sample.invitation.api;
import com.abluepoint.summer.sample.invitation.application.InvitationContextCommand;

public record InvitationContextRequest(String enterpriseId) {
    InvitationContextCommand command() { return new InvitationContextCommand(enterpriseId); }
}
