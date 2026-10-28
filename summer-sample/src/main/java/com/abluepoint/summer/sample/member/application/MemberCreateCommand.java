package com.abluepoint.summer.sample.member.application;

public record MemberCreateCommand(
        String subject,
        String displayName,
        String email) {}
