package com.abluepoint.summer.sample.mcpauthorization;

/** Narrow seam between MCP authorization and the HumanApproval lifecycle. */
public interface HumanApprovalPort {
    String createPending(HumanApprovalBinding binding);

    boolean consumeApproved(String approvalId, HumanApprovalBinding binding);
}
