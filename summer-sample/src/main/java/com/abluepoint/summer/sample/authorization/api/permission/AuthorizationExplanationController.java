package com.abluepoint.summer.sample.authorization.api.permission;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.sample.authorization.application.permission.AuthorizationExplanationService;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/enterprises/{enterpriseId}/authorization", "/api/v1/platform/authorization"})
public class AuthorizationExplanationController {
  private final AuthorizationExplanationService explanations;
  private final RequestIdAccessor requests;

  public AuthorizationExplanationController(AuthorizationExplanationService explanations,
      RequestIdAccessor requests) {
    this.explanations = explanations; this.requests = requests;
  }

  @GetMapping("/explanation")
  public Object explain(HttpServletRequest request, Authentication manager,
      @RequestParam MultiValueMap<String, String> query) {
    if (query.values().stream().anyMatch(values -> values.size() != 1))
      throw SummerException.withMessage(PermissionErrorCode.INVALID, "Single diagnostic parameter values required");
    String requestId = requests.currentRequestId();
    return ApiResponse.success(explanations.explain(PermissionController.domain(request), manager,
        query.toSingleValueMap(), requestId), requestId);
  }
}
