package com.abluepoint.summer.sample.release;

import com.abluepoint.summer.common.error.api.CommonErrorCode;
import com.abluepoint.summer.common.error.api.ErrorCode;
import com.abluepoint.summer.mvc.error.spi.ErrorStatusResolver;
import com.abluepoint.summer.common.response.api.ApiError;
import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.response.api.RawResponse;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@PreAuthorize("isAuthenticated()")
class ReleaseGateController {
    private final ReleaseGateService service; private final RequestIdAccessor requestId;
    private final ErrorStatusResolver errorStatus;
    ReleaseGateController(ReleaseGateService s, RequestIdAccessor r, ErrorStatusResolver errorStatus) {
        service = s; requestId = r; this.errorStatus = errorStatus;
    }
    @GetMapping("/internal/v1/release/performance-budgets") @RawResponse ResponseEntity<ApiResponse<PerformanceBudgetView>> budgets(Authentication a) { try { return ok(service.budgets(a)); } catch (Exception e) { return err(e); } }
    @GetMapping("/internal/v1/release/readiness") @RawResponse ResponseEntity<ApiResponse<ReadinessView>> readiness(Authentication a) { try { return ok(service.readiness(a)); } catch (Exception e) { return err(e); } }
    @PostMapping(value="/internal/v1/release/performance-benchmarks", consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<PerformanceBenchmarkView>> benchmark(@RequestBody PerformanceBenchmarkRequest q,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication a){try{return ok(service.benchmark(q,k,a));}catch(Exception e){return err(e);}}
    @PostMapping(value="/internal/v1/release/gates:validate", consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<ReleaseGateView>> validate(@RequestBody ReleaseGateRequest q,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication a){try{return ok(service.validate(q,k,a));}catch(Exception e){return err(e);}}
    @PostMapping(value="/internal/v1/release/rollback:dry-run", consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<RollbackDryRunView>> rollback(@RequestBody RollbackDryRunRequest q,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication a){try{return ok(service.rollback(q,k,a));}catch(Exception e){return err(e);}}
    @PostMapping(value="/internal/v1/release/failure-drills", consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<FailureDrillView>> drill(@RequestBody FailureDrillRequest q,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication a){try{return ok(service.drill(q,k,a));}catch(Exception e){return err(e);}}
    private<T>ResponseEntity<ApiResponse<T>>ok(T value){return ResponseEntity.ok().header("X-Request-Id",requestId.currentRequestId()).body(ApiResponse.success(value,requestId.currentRequestId()));}
    private <T> ResponseEntity<ApiResponse<T>> err(Exception exception) {
        ErrorCode code = CommonErrorCode.INTERNAL_ERROR;
        if (exception instanceof org.springframework.security.access.AccessDeniedException) code = CommonErrorCode.FORBIDDEN;
        else if (exception instanceof ReleaseValidationException) code = ReleaseErrorCode.INVALID;
        else if (exception instanceof ReleaseIdempotencyConflictException) code = CommonErrorCode.CONFLICT;
        String message = code == CommonErrorCode.INTERNAL_ERROR ? code.defaultMessage() : exception.getMessage();
        String rid = requestId.currentRequestId();
        return ResponseEntity.status(errorStatus.resolve(code)).header("X-Request-Id", rid)
                .body(ApiResponse.failure(new ApiError(code.code(), message, List.of()), rid));
    }
}
