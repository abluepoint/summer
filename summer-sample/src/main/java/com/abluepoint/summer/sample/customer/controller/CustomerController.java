package com.abluepoint.summer.sample.customer.controller;

import com.abluepoint.summer.sample.customer.model.command.CustomerCreateCommand;
import com.abluepoint.summer.sample.customer.model.command.CustomerUpdateCommand;
import com.abluepoint.summer.sample.customer.model.request.CustomerCreateRequest;
import com.abluepoint.summer.sample.customer.model.request.CustomerListRequest;
import com.abluepoint.summer.sample.customer.model.request.CustomerUpdateRequest;
import com.abluepoint.summer.sample.customer.model.view.CustomerPage;
import com.abluepoint.summer.sample.customer.model.view.CustomerView;
import com.abluepoint.summer.sample.customer.service.CustomerService;

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
public class CustomerController {
    private final CustomerService service;
    private final RequestIdAccessor requestId;
    CustomerController(CustomerService service, RequestIdAccessor requestId) { this.service = service; this.requestId = requestId; }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_customer.read')")
    @Field(param="page", required=false, style="customerPageStyle") @Field(param="size", required=false, style="customerPageSizeStyle")
    @Field(param="active", required=false, style="booleanStyle") @Field(param="sort", required=false, style="customerSortStyle")
    @Field(param="direction", required=false, style="customerSortDirectionStyle") @Field(param="projection", required=false, style="customerProjectionStyle")
    CustomerPage list(FieldValues values) {
        Integer page=values.get("page",Integer.class), size=values.get("size",Integer.class); String sort=values.get("sort",String.class), direction=values.get("direction",String.class), projection=values.get("projection",String.class);
        return service.list(new CustomerListRequest(page==null?0:page,size==null?20:size,values.get("active",Boolean.class),sortField(sort),"desc".equals(direction)?CustomerListRequest.SortDirection.DESC:CustomerListRequest.SortDirection.ASC,"summary".equals(projection)?CustomerListRequest.Projection.SUMMARY:CustomerListRequest.Projection.DETAIL));
    }
    @GetMapping("/{username}") @PreAuthorize("hasAuthority('SCOPE_customer.read')")
    @Field(param="username", required=true, style="customerUsernameStyle")
    CustomerView find(FieldValues values) { return service.findByUsername(values.get("username",String.class)); }

    @PostMapping @PreAuthorize("hasAuthority('SCOPE_customer.write')") @FieldContract
    @Field(param="username", required=true, style="customerUsernameStyle") @Field(param="email", required=true, style="customerEmailStyle") @Field(param="displayName", required=true, style="customerDisplayNameStyle")
    CustomerView create(FieldValues values, @Valid @RequestBody CustomerCreateRequest request, Authentication authentication) { return service.create(new CustomerCreateCommand(values.get("username",String.class),values.get("email",String.class),values.get("displayName",String.class)), requestId.currentRequestId(), authentication); }

    @PatchMapping("/{username}") @PreAuthorize("hasAuthority('SCOPE_customer.write')") @FieldContract
    @Field(param="username", required=true, style="customerUsernameStyle")
    @Field(param="email", required=false, style="customerEmailStyle")
    @Field(param="displayName", required=false, style="customerDisplayNameStyle")
    @Field(param="active", required=false, style="booleanStyle")
    CustomerView update(FieldValues values, @Valid @RequestBody CustomerUpdateRequest request, Authentication authentication) {
        Boolean active=values.get("active",Boolean.class);
        return service.update(values.get("username",String.class),new CustomerUpdateCommand(values.contains("email"),values.get("email",String.class),values.contains("displayName"),values.get("displayName",String.class),values.contains("active"),Boolean.TRUE.equals(active)), requestId.currentRequestId(), authentication);
    }
    @DeleteMapping("/{username}") @RawResponse @PreAuthorize("hasAuthority('SCOPE_customer.write')")
    @Field(param="username", required=true, style="customerUsernameStyle")
    ResponseEntity<Void> delete(FieldValues values, Authentication authentication) { service.delete(values.get("username",String.class), requestId.currentRequestId(), authentication); return ResponseEntity.noContent().header("X-Request-Id", requestId.currentRequestId()).build(); }
    private static CustomerListRequest.SortField sortField(String value) { return switch(value==null?"username":value){case "username"->CustomerListRequest.SortField.USERNAME; case "email"->CustomerListRequest.SortField.EMAIL; case "displayName"->CustomerListRequest.SortField.DISPLAY_NAME; case "active"->CustomerListRequest.SortField.ACTIVE; default->CustomerListRequest.SortField.USERNAME;}; }
}
