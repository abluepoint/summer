package com.abluepoint.summer.sample.member;

record MemberSummary(String memberId, String displayName, String email, String status) {
    public String id() { return memberId; }
}
