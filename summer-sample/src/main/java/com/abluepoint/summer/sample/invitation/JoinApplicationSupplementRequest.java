package com.abluepoint.summer.sample.invitation;

import java.util.List;

record JoinApplicationSupplementRequest(List<String> materials) {
    JoinApplicationSupplementRequest {
        materials = materials == null ? List.of() : List.copyOf(materials);
    }
}
