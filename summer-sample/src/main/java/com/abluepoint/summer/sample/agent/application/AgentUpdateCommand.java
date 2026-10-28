package com.abluepoint.summer.sample.agent.application;

import java.util.List;

public record AgentUpdateCommand(String name, String description, String ownerMemberId, String runtimeType) {}
