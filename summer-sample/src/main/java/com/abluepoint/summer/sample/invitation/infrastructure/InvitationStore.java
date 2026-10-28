package com.abluepoint.summer.sample.invitation.infrastructure;

import java.util.*;
import com.abluepoint.summer.sample.invitation.application.InvitationAuditView;

public final class InvitationStore {
    public final Map<String, InvitationEntity> invitations = new LinkedHashMap<>();
    public final Map<String, String> tokenIndex = new HashMap<>();
    public final Map<String, JoinApplicationEntity> applications = new LinkedHashMap<>();
    public final Map<String, InvitationReceipt> idempotency = new HashMap<>();
    public final Map<String, List<InvitationAuditView>> audits = new HashMap<>();
}
