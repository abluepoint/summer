package com.abluepoint.summer.sample.customer.model.request;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
public record CustomerUpdateRequest(@Email @Size(max = 200) String email, @Size(max = 200) String displayName, Boolean active) {}
