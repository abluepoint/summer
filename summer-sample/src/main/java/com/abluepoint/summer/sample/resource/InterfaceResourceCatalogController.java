package com.abluepoint.summer.sample.resource;

import com.abluepoint.summer.common.response.ApiError;
import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@ResourcePermission("platform.interface-resources.read")
final class InterfaceResourceCatalogController {

    private final InterfaceResourceCatalogService service;
    private final RequestIdAccessor requestIdAccessor;

    InterfaceResourceCatalogController(
            InterfaceResourceCatalogService service,
            RequestIdAccessor requestIdAccessor
    ) {
        this.service = service;
        this.requestIdAccessor = requestIdAccessor;
    }

    @GetMapping("/api/v1/platform/interface-resources")
    @RawResponse
    ResponseEntity<ApiResponse<InterfaceResourceCatalogView>> read(
            Authentication authentication,
            HttpServletRequest request
    ) {
        String requestId = requestIdAccessor.currentRequestId();
        try {
            InterfaceResourceCatalogView view = service.read(authentication);
            String etag = "\"" + view.revision() + "\"";
            if (etag.equals(request.getHeader("If-None-Match"))) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(etag)
                        .header("X-Request-Id", requestId)
                        .build();
            }
            return ResponseEntity.ok()
                    .eTag(etag)
                    .header("X-Request-Id", requestId)
                    .body(ApiResponse.success(view, requestId));
        } catch (AccessDeniedException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .header("X-Request-Id", requestId)
                    .body(ApiResponse.failure(
                            new ApiError("SUMMER_FORBIDDEN", exception.getMessage(), List.of()), requestId));
        }
    }
}
