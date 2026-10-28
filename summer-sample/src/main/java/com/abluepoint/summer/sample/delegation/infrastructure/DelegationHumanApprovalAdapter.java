package com.abluepoint.summer.sample.delegation.infrastructure;
import com.abluepoint.summer.sample.delegation.application.DelegationService;
import com.abluepoint.summer.sample.mcpauthorization.domain.HumanApprovalBinding;
import com.abluepoint.summer.sample.mcpauthorization.domain.HumanApprovalPort;

import com.abluepoint.summer.sample.mcpauthorization.domain.HumanApprovalBinding;
import com.abluepoint.summer.sample.mcpauthorization.domain.HumanApprovalPort;
import org.springframework.stereotype.Component;

/** Sample adapter that keeps decision-created approvals in the delegation store. */
@Component
public final class DelegationHumanApprovalAdapter implements HumanApprovalPort {
    private final DelegationService service;

    DelegationHumanApprovalAdapter(DelegationService service) {
        this.service = service;
    }

    @Override
    public String createPending(HumanApprovalBinding binding) {
        return service.createBoundApproval(binding);
    }

    @Override
    public boolean consumeApproved(String approvalId, HumanApprovalBinding binding) {
        return service.consumeBoundApproval(approvalId, binding);
    }
}
