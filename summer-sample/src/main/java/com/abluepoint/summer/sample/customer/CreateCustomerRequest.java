package com.abluepoint.summer.sample.customer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
record CreateCustomerRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Email @Size(max = 200) String email, @NotBlank @Size(max = 200) String displayName) {}
