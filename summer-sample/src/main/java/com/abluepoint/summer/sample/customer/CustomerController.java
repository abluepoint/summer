package com.abluepoint.summer.sample.customer;

import com.abluepoint.summer.mvc.annotation.Field;
import com.abluepoint.summer.mvc.annotation.FieldContract;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.validate.FieldValues;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
class CustomerController {
    private final CustomerService service;
    private final RequestIdAccessor requestId;
    CustomerController(CustomerService service, RequestIdAccessor requestId) { this.service = service; this.requestId = requestId; }

    @GetMapping
    @PreAuthorize("@businessMethodAccess.require(authentication, 'customer.read') or (hasAuthority('SCOPE_customer.read'))")
    @Field(param="page", required=false, style="customerPageStyle") @Field(param="size", required=false, style="customerPageSizeStyle")
    @Field(param="active", required=false, style="booleanStyle") @Field(param="sort", required=false, style="customerSortStyle")
    @Field(param="direction", required=false, style="customerSortDirectionStyle") @Field(param="projection", required=false, style="customerProjectionStyle")
    CustomerPage list(FieldValues values) {
        Integer page=values.get("page",Integer.class), size=values.get("size",Integer.class); String sort=values.get("sort",String.class), direction=values.get("direction",String.class), projection=values.get("projection",String.class);
        return service.list(new CustomerListQuery(page==null?0:page,size==null?20:size,values.get("active",Boolean.class),sortField(sort),"desc".equals(direction)?CustomerListQuery.SortDirection.DESC:CustomerListQuery.SortDirection.ASC,"summary".equals(projection)?CustomerListQuery.Projection.SUMMARY:CustomerListQuery.Projection.DETAIL));
    }
    @GetMapping("/{username}") @PreAuthorize("@businessMethodAccess.require(authentication, 'customer.read') or (hasAuthority('SCOPE_customer.read'))")
    @Field(param="username", required=true, style="customerUsernameStyle")
    CustomerView find(FieldValues values) { return service.findByUsername(values.get("username",String.class)); }

    @PostMapping @PreAuthorize("@businessMethodAccess.require(authentication, 'customer.write') or (hasAuthority('SCOPE_customer.write'))") @FieldContract
    @Field(param="username", required=true, style="customerUsernameStyle") @Field(param="email", required=true, style="customerEmailStyle") @Field(param="displayName", required=true, style="customerDisplayNameStyle")
    CustomerView create(FieldValues values, @Valid @RequestBody CreateCustomerRequest request, Authentication authentication) { return service.create(new CreateCustomerCommand(values.get("username",String.class),values.get("email",String.class),values.get("displayName",String.class)), requestId.currentRequestId(), authentication); }

    @PatchMapping("/{username}") @PreAuthorize("@businessMethodAccess.require(authentication, 'customer.write') or (hasAuthority('SCOPE_customer.write'))") @FieldContract
    @Field(param="username", required=true, style="customerUsernameStyle")
    @Field(param="email", required=false, style="customerEmailStyle")
    @Field(param="displayName", required=false, style="customerDisplayNameStyle")
    @Field(param="active", required=false, style="booleanStyle")
    CustomerView update(FieldValues values, @Valid @RequestBody UpdateCustomerRequest request, Authentication authentication) {
        Boolean active=values.get("active",Boolean.class);
        return service.update(values.get("username",String.class),new UpdateCustomerCommand(values.contains("email"),values.get("email",String.class),values.contains("displayName"),values.get("displayName",String.class),values.contains("active"),Boolean.TRUE.equals(active)), requestId.currentRequestId(), authentication);
    }
    @DeleteMapping("/{username}") @RawResponse @PreAuthorize("@businessMethodAccess.require(authentication, 'customer.write') or (hasAuthority('SCOPE_customer.write'))")
    @Field(param="username", required=true, style="customerUsernameStyle")
    ResponseEntity<Void> delete(FieldValues values, Authentication authentication) { service.delete(values.get("username",String.class), requestId.currentRequestId(), authentication); return ResponseEntity.noContent().header("X-Request-Id", requestId.currentRequestId()).build(); }
    private static CustomerListQuery.SortField sortField(String value) { return switch(value==null?"username":value){case "username"->CustomerListQuery.SortField.USERNAME; case "email"->CustomerListQuery.SortField.EMAIL; case "displayName"->CustomerListQuery.SortField.DISPLAY_NAME; case "active"->CustomerListQuery.SortField.ACTIVE; default->CustomerListQuery.SortField.USERNAME;}; }
}
