package com.abluepoint.summer.sample.release;

import com.abluepoint.summer.mvc.annotation.Field;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.function.Supplier;
import org.springframework.security.access.AccessDeniedException;

@RestController
@PreAuthorize("isAuthenticated()")
class ReleaseGateController {
    private final ReleaseGateService service; private final RequestIdAccessor requestId;
    ReleaseGateController(ReleaseGateService s, RequestIdAccessor r) {
        service = s; requestId = r;
    }
    @GetMapping("/internal/v1/release/performance-budgets") @RawResponse ResponseEntity<ApiResponse<PerformanceBudgetView>> budgets(Authentication a) { return ok(nativeBoundary(() -> service.budgets(a))); }
    @GetMapping("/internal/v1/release/readiness") @RawResponse ResponseEntity<ApiResponse<ReadinessView>> readiness(Authentication a) { return ok(nativeBoundary(() -> service.readiness(a))); }
    @PostMapping(value="/internal/v1/release/performance-benchmarks", consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<PerformanceBenchmarkView>> benchmark(@RequestBody PerformanceBenchmarkRequest q,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication a){return ok(nativeBoundary(() -> service.benchmark(q,k,a)));}
    @PostMapping(value="/internal/v1/release/gates:validate", consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<ReleaseGateView>> validate(@RequestBody ReleaseGateRequest q,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication a){return ok(nativeBoundary(() -> service.validate(q,k,a)));}
    @Field(param = "releaseVersion", required = false, nullable = true, style = "requestTextStyle")
    @Field(param = "targetVersion", required = false, nullable = true, style = "requestTextStyle")
    @Field(param = "reason", required = false, nullable = true, style = "requestTextStyle")
    @PostMapping(value="/internal/v1/release/rollback:dry-run", consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<RollbackDryRunView>> rollback(@RequestBody RollbackDryRunRequest q,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication a){return ok(nativeBoundary(() -> service.rollback(q,k,a)));}
    @Field(param = "component", required = false, nullable = true, style = "requestTextStyle")
    @Field(param = "scenario", required = false, nullable = true, style = "requestTextStyle")
    @PostMapping(value="/internal/v1/release/failure-drills", consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<FailureDrillView>> drill(@RequestBody FailureDrillRequest q,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication a){return ok(nativeBoundary(() -> service.drill(q,k,a)));}
    private<T>ResponseEntity<ApiResponse<T>>ok(T value){return ResponseEntity.ok().header("X-Request-Id",requestId.currentRequestId()).body(ApiResponse.success(value,requestId.currentRequestId()));}
    // Native permission failures retain the existing public business message.
    private <T> T nativeBoundary(Supplier<T> call) {
        try { return call.get(); }
        catch (AccessDeniedException denied) {
            throw SummerException.withMessage(CommonErrorCode.FORBIDDEN, denied.getMessage(), denied);
        }
    }
}
