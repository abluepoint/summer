package com.abluepoint.summer.sample.invitation.application;
import java.util.*;

public record JoinApplicationSupplementCommand(List<String> materials) {
    public JoinApplicationSupplementCommand {
        materials = materials == null ? List.of() : List.copyOf(materials);
    }

    @Override public String toString() { return "JoinApplicationSupplementRequest[" + "materials=" + materials + "]"; }
}
