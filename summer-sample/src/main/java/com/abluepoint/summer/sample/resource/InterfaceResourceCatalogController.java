package com.abluepoint.summer.sample.resource;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController

final class InterfaceResourceCatalogController {

    private final InterfaceResourceCatalogService service;
    private final RequestIdAccessor requestIdAccessor;
    private final InterfaceResourceHistory history;

    InterfaceResourceCatalogController(
            InterfaceResourceCatalogService service,
            RequestIdAccessor requestIdAccessor, InterfaceResourceHistory history
    ) {
        this.service = service;
        this.requestIdAccessor = requestIdAccessor;
        this.history = history;
    }

    @GetMapping("/api/v1/platform/interface-resources")
    @RawResponse
    ResponseEntity<?> read(
            Authentication authentication,
            HttpServletRequest request,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long revision,
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        String requestId = requestIdAccessor.currentRequestId();
        try {
            Object view;
            String etag;
            if (page != null || size != null) {
                var slice = service.readPage(authentication, page == null ? 0 : page, size == null ? 10 : size, revision, refresh);
                view = slice;
                etag = "\"" + slice.revision() + "-p" + slice.page() + "-s" + slice.size() + "\"";
            } else {
                var catalog = service.read(authentication);
                view = catalog;
                etag = "\"" + catalog.revision() + "\"";
            }
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
            throw SummerException.withMessage(CommonErrorCode.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException invalid) {
            throw SummerException.withMessage(InterfaceResourceErrorCode.PAGE_INVALID, invalid.getMessage(), invalid);
        }
    }
    @GetMapping("/api/v1/platform/interface-resources/history")
    @RawResponse
    ResponseEntity<?> history(Authentication authentication, @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size) {
        return historyResponse(authentication, () -> java.util.Map.of("items", history.list(page, size), "page", page, "size", size, "total", history.count()));
    }

    @GetMapping("/api/v1/platform/interface-resources/history/{revision}")
    @RawResponse
    ResponseEntity<?> revision(Authentication authentication, @PathVariable long revision) {
        return historyResponse(authentication, () -> history.read(revision));
    }

    @GetMapping("/api/v1/platform/interface-resources/diff")
    @RawResponse
    ResponseEntity<?> diff(Authentication authentication, @RequestParam long from, @RequestParam long to) {
        return historyResponse(authentication, () -> history.diff(from, to));
    }

    private ResponseEntity<?> historyResponse(Authentication authentication, java.util.function.Supplier<?> value) {
        String requestId = requestIdAccessor.currentRequestId();
        try {
            service.requireRead(authentication);
            return ResponseEntity.ok().header("X-Request-Id", requestId).body(ApiResponse.success(value.get(), requestId));
        } catch (AccessDeniedException denied) {
            throw SummerException.withMessage(CommonErrorCode.FORBIDDEN, "Interface resource catalog denied", denied);
        } catch (IllegalArgumentException invalid) {
            throw SummerException.withMessage(InterfaceResourceErrorCode.HISTORY_INVALID, invalid.getMessage(), invalid);
        }
    }

}
