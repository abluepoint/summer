package com.abluepoint.summer.sample.release;

import com.abluepoint.summer.common.response.ApiError;
import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@PreAuthorize("isAuthenticated()")
class ReleaseGateController {
    private final ReleaseGateService service; private final RequestIdAccessor requestId;
    ReleaseGateController(ReleaseGateService s, RequestIdAccessor r) { service = s; requestId = r; }
    @GetMapping("/internal/v1/release/performance-budgets") @RawResponse ResponseEntity<ApiResponse<PerformanceBudgetView>> budgets(Authentication a) { try { return ok(service.budgets(a)); } catch (Exception e) { return err(e); } }
    @GetMapping("/internal/v1/release/readiness") @RawResponse ResponseEntity<ApiResponse<ReadinessView>> readiness(Authentication a) { try { return ok(service.readiness(a)); } catch (Exception e) { return err(e); } }
    @PostMapping(value="/internal/v1/release/performance-benchmarks", consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<PerformanceBenchmarkView>> benchmark(@RequestBody PerformanceBenchmarkRequest q,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication a){try{return ok(service.benchmark(q,k,a));}catch(Exception e){return err(e);}}
    @PostMapping(value="/internal/v1/release/gates:validate", consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<ReleaseGateView>> validate(@RequestBody ReleaseGateRequest q,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication a){try{return ok(service.validate(q,k,a));}catch(Exception e){return err(e);}}
    @PostMapping(value="/internal/v1/release/rollback:dry-run", consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<RollbackDryRunView>> rollback(@RequestBody RollbackDryRunRequest q,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication a){try{return ok(service.rollback(q,k,a));}catch(Exception e){return err(e);}}
    @PostMapping(value="/internal/v1/release/failure-drills", consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<FailureDrillView>> drill(@RequestBody FailureDrillRequest q,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication a){try{return ok(service.drill(q,k,a));}catch(Exception e){return err(e);}}
    private<T>ResponseEntity<ApiResponse<T>>ok(T value){return ResponseEntity.ok().header("X-Request-Id",requestId.currentRequestId()).body(ApiResponse.success(value,requestId.currentRequestId()));}
    private<T>ResponseEntity<ApiResponse<T>>err(Exception e){HttpStatus s=HttpStatus.INTERNAL_SERVER_ERROR;String c="SUMMER_INTERNAL_ERROR";if(e instanceof org.springframework.security.access.AccessDeniedException){s=HttpStatus.FORBIDDEN;c="SUMMER_FORBIDDEN";}else if(e instanceof ReleaseValidationException){s=HttpStatus.BAD_REQUEST;c="IAM_RELEASE_INVALID";}else if(e instanceof ReleaseIdempotencyConflictException){s=HttpStatus.CONFLICT;c="SUMMER_IDEMPOTENCY_CONFLICT";}return ResponseEntity.status(s).header("X-Request-Id",requestId.currentRequestId()).body(ApiResponse.failure(new ApiError(c,e.getMessage(),List.of()),requestId.currentRequestId()));}
}
