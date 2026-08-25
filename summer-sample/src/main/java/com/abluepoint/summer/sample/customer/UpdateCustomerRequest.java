package com.abluepoint.summer.sample.customer;

import jakarta.validation.constraints.Size;

record UpdateCustomerRequest(
        @Size(max = 200) String displayName,
        Boolean active
) {
}
