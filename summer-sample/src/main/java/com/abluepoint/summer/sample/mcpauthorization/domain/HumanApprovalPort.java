package com.abluepoint.summer.sample.mcpauthorization.domain;


/** Narrow seam between MCP authorization and the HumanApproval lifecycle. */
@org.springframework.modulith.NamedInterface("approval")
public interface HumanApprovalPort {
    String createPending(HumanApprovalBinding binding);

    boolean consumeApproved(String approvalId, HumanApprovalBinding binding);
}
