package com.abluepoint.summer.sample.authorization;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ManagedRequestDomainTest {
    @Test void anotherGlobalControllerCannotSelectTenantThroughAnUnrelatedQuery() throws Exception {
        var service = org.mockito.Mockito.mock(AuthorizationService.class);
        var interceptor = LegacyInterceptorFixture.create(service, () -> "global-event-control");
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/other-global");
        request.addParameter("enterpriseId", "ent-other");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/api/other-global");
        var handler = new org.springframework.web.method.HandlerMethod(new OtherGlobalController(), OtherGlobalController.class.getDeclaredMethod("read"));
        assertThat(com.abluepoint.summer.sample.authorization.api.security.ManagedRequestAuthorization.domain(request)).isNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> interceptor.preHandle(request,new org.springframework.mock.web.MockHttpServletResponse(),handler))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class).hasMessage("Current managed resource adapter required");
        org.mockito.Mockito.verifyNoInteractions(service);
    }
    static class OtherGlobalController {
        public void read() {}
    }

}
