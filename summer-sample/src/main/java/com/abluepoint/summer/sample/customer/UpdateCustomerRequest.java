package com.abluepoint.summer.sample.customer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
record UpdateCustomerRequest(@Email @Size(max = 200) String email, @Size(max = 200) String displayName, Boolean active) {}
