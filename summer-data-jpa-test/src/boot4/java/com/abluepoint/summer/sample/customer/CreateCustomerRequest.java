package com.abluepoint.summer.sample.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CreateCustomerRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(max = 200) String displayName
) {
}
