package com.abluepoint.summer.sample.delegation;

import com.abluepoint.summer.sample.mcpauthorization.HumanApprovalBinding;
import com.abluepoint.summer.sample.mcpauthorization.HumanApprovalPort;
import org.springframework.stereotype.Component;

/** Sample adapter that keeps decision-created approvals in the delegation store. */
@Component
final class DelegationHumanApprovalAdapter implements HumanApprovalPort {
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
