package com.abluepoint.summer.sample.member.domain;



public class MemberConflictException extends RuntimeException {
    public MemberConflictException() { super("Subject already has a membership in this enterprise"); }
}
