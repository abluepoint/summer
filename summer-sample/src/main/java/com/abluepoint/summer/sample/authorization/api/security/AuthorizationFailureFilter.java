package com.abluepoint.summer.sample.authorization.api.security;

import com.abluepoint.summer.common.error.SummerException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** Route pre-dispatch application failures through the same global error handler as MVC. */
@Component
@Order(-101)
public class AuthorizationFailureFilter extends OncePerRequestFilter {
  private final ObjectProvider<HandlerExceptionResolver> resolvers;

  public AuthorizationFailureFilter(
      @Qualifier("handlerExceptionResolver") ObjectProvider<HandlerExceptionResolver> resolvers) {
    this.resolvers = resolvers;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      chain.doFilter(request, response);
    } catch (SummerException failure) {
      var resolver = resolvers.getIfAvailable();
      if (response.isCommitted()
          || resolver == null
          || resolver.resolveException(request, response, null, failure) == null) throw failure;
    } catch (ServletException wrapper) {
      if (response.isCommitted() || !(wrapper.getCause() instanceof SummerException failure))
        throw wrapper;
      var resolver = resolvers.getIfAvailable();
      if (resolver == null
          || resolver.resolveException(request, response, null, failure) == null) throw wrapper;
    }
  }
}
