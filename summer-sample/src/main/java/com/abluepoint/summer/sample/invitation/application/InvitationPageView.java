package com.abluepoint.summer.sample.invitation.application;
import java.util.*;
import java.time.Instant;

public record InvitationPageView<T>(List<T> items, int page, int pageSize, int total) {}
