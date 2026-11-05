package com.abluepoint.summer.sample.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.api.security.AuthorizationFailureFilter;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

class AuthorizationFailureFilterTest {
  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/extension");
  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private final AtomicReference<Exception> resolved = new AtomicReference<>();

  private AuthorizationFailureFilter filter(boolean handled) {
    var beans = new DefaultListableBeanFactory();
    HandlerExceptionResolver resolver = (request, response, handler, failure) -> {
      assertThat(handler).isNull();
      resolved.set(failure);
      return handled ? new ModelAndView() : null;
    };
    beans.registerSingleton("resolver", resolver);
    return new AuthorizationFailureFilter(beans.getBeanProvider(HandlerExceptionResolver.class));
  }

  private SummerException unavailable() {
    return SummerException.withMessage(SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE,
        "The authoritative authorization store is unavailable");
  }

  @Test void directApplicationFailureUsesTheGlobalResolver() throws Exception {
    var failure = unavailable();
    filter(true).doFilter(request, response, (req, res) -> { throw failure; });
    assertThat(resolved.get()).isSameAs(failure);
  }

  @Test void servletWrappedApplicationFailureUsesTheSameGlobalResolver() throws Exception {
    var failure = unavailable();
    filter(true).doFilter(request, response, (req, res) -> { throw new ServletException(failure); });
    assertThat(resolved.get()).isSameAs(failure);
  }

  @Test void committedResponsePreservesTheOriginalServletFailure() throws Exception {
    var wrapped = new ServletException(unavailable());
    response.flushBuffer();
    assertThatThrownBy(() -> filter(true).doFilter(request, response,
        (req, res) -> { throw wrapped; })).isSameAs(wrapped);
    assertThat(resolved.get()).isNull();
  }

  @Test void unresolvedApplicationFailurePreservesTheOriginalServletFailure() {
    var wrapped = new ServletException(unavailable());
    assertThatThrownBy(() -> filter(false).doFilter(request, response,
        (req, res) -> { throw wrapped; })).isSameAs(wrapped);
    assertThat(resolved.get()).isSameAs(wrapped.getCause());
  }

  @Test void unrelatedServletFailureIsNotReclassified() {
    var wrapped = new ServletException(new IllegalStateException("unrelated"));
    assertThatThrownBy(() -> filter(true).doFilter(request, response,
        (req, res) -> { throw wrapped; })).isSameAs(wrapped);
    assertThat(resolved.get()).isNull();
  }

  @Test void nestedApplicationFailureIsNotUnwrappedRecursively() {
    var wrapped = new ServletException(new ServletException(unavailable()));
    assertThatThrownBy(() -> filter(true).doFilter(request, response,
        (req, res) -> { throw wrapped; })).isSameAs(wrapped);
    assertThat(resolved.get()).isNull();
  }

  @Test void ioFailureIsNotReclassified() {
    var failure = new IOException("transport");
    assertThatThrownBy(() -> filter(true).doFilter(request, response,
        (req, res) -> { throw failure; })).isSameAs(failure);
    assertThat(resolved.get()).isNull();
  }
}
