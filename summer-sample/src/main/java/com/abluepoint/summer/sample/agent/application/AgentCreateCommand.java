package com.abluepoint.summer.sample.agent.application;

import java.util.List;

public record AgentCreateCommand(String name, String code, String description, String ownerMemberId, String runtimeType) {}
