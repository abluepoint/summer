package com.abluepoint.summer.sample.invitation.application;
import java.util.*;

public record InvitationContextCommand(String enterpriseId) {
    @Override public String toString() { return "InvitationContextRequest[" + "enterpriseId=" + enterpriseId + "]"; }
}
