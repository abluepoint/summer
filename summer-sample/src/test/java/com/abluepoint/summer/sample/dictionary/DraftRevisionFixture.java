package com.abluepoint.summer.sample.dictionary;

/** Legacy HTTP fixtures now read a draft revision before each conditional write. */
public final class DraftRevisionFixture {
    private DraftRevisionFixture() {}
    public static String revised(org.springframework.test.web.servlet.MockMvc http, String path, String body) throws Exception {
        String tenant = path.contains("/enterprises/") ? path.split("/enterprises/")[1].split("/")[0] : "ent-acme";
        String base = path.contains("/items") ? path.substring(0, path.indexOf("/items")) : path;
        var response = http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(base + "/draft")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject("operator").claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId",tenant,"status","ACTIVE"))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform.dictionary.read"))))
            .andReturn().getResponse();
        if (response.getStatus() != 200) return body;
        var mapper = new tools.jackson.databind.ObjectMapper();
        long revision = mapper.readTree(response.getContentAsString()).path("data").path("draftRevision").asLong();
        return "{\"expectedDraftRevision\":" + revision + "," + body.stripLeading().substring(1);
    }
    private static final java.util.Map<org.springframework.test.web.servlet.MockMvc, java.util.Map<String,String>> receipts = new java.util.WeakHashMap<>();
    public static String lifecycle(org.springframework.test.web.servlet.MockMvc http, String path, String body) throws Exception {
        var mapper = new tools.jackson.databind.ObjectMapper();
        var input = (tools.jackson.databind.node.ObjectNode)mapper.readTree(body);
        String key = path + body;
        if (input.has("idempotencyKey")) {
            var cached = receipts.computeIfAbsent(http, ignored -> new java.util.HashMap<>()).get(key);
            if (cached != null) return cached;
        }
        String base = path.replaceFirst("/(?:versions/)?(?:publish|disable|rollback)$", "");
        // Read management HTTP with the same fixture actor, preserving explicit stale versions.
        String tenant = path.contains("/enterprises/") ? path.split("/enterprises/")[1].split("/")[0] : "ent-acme";
        var response = http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(base + "/draft")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                    .jwt(j -> j.subject("operator").claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId",tenant,"status","ACTIVE"))))
                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform.dictionary.read"))))
                .andReturn().getResponse();
        if (response.getStatus() != 200) return body;
        var draft = mapper.readTree(response.getContentAsString()).path("data");
        if (!input.has("expectedEffectiveRevision")) input.put("expectedEffectiveRevision", draft.path("effectiveRevision").asLong());
        if (path.endsWith("/publish") && !input.has("expectedDraftRevision")) input.put("expectedDraftRevision", draft.path("draftRevision").asLong());
        String result = mapper.writeValueAsString(input);
        if (input.has("idempotencyKey")) receipts.get(http).put(key, result);
        return result;
    }
}
