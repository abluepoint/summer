package com.abluepoint.summer.sample.customer;

import com.abluepoint.summer.mvc.annotation.Field;
import com.abluepoint.summer.mvc.annotation.FieldContract;
import com.abluepoint.summer.mvc.annotation.FieldSource;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.validate.FieldValues;
import com.abluepoint.summer.sample.shared.CustomerView;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
class CustomerController {

    private final CustomerService service;

    CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_customer.read')")
    @Field(param = "page", source = FieldSource.PARAM, style = "customerPageStyle")
    @Field(param = "size", source = FieldSource.PARAM, style = "customerPageSizeStyle")
    @Field(param = "active", source = FieldSource.PARAM, style = "booleanStyle")
    @Field(param = "sort", source = FieldSource.PARAM, style = "customerSortStyle")
    @Field(param = "direction", source = FieldSource.PARAM, style = "customerSortDirectionStyle")
    @Field(param = "projection", source = FieldSource.PARAM, style = "customerProjectionStyle")
    CustomerPage list(FieldValues values) {
        Integer page = values.get("page", Integer.class);
        Integer size = values.get("size", Integer.class);
        String sort = values.get("sort", String.class);
        String direction = values.get("direction", String.class);
        String projection = values.get("projection", String.class);
        CustomerListQuery query = new CustomerListQuery(
                page == null ? CustomerListQuery.DEFAULT_PAGE : page,
                size == null ? CustomerListQuery.DEFAULT_SIZE : size,
                values.get("active", Boolean.class),
                sortField(sort),
                "desc".equals(direction)
                        ? CustomerListQuery.SortDirection.DESC
                        : CustomerListQuery.SortDirection.ASC,
                "summary".equals(projection)
                        ? CustomerListQuery.Projection.SUMMARY
                        : CustomerListQuery.Projection.DETAIL
        );
        return service.list(query);
    }

    @GetMapping("/{email}")
    @PreAuthorize("hasAuthority('SCOPE_customer.read')")
    @Field(
            param = "email",
            source = FieldSource.PATH,
            required = true,
            style = "customerEmailStyle"
    )
    CustomerView findByEmail(FieldValues values) {
        return service.findByEmail(values.get("email", String.class));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_customer.write')")
    @FieldContract(rejectUnknownJson = true)
    @Field(
            param = "email",
            source = FieldSource.JSON,
            required = true,
            style = "customerEmailStyle"
    )
    @Field(
            param = "displayName",
            source = FieldSource.JSON,
            required = true,
            style = "customerDisplayNameStyle"
    )
    CustomerView create(FieldValues values, @Valid @RequestBody CreateCustomerRequest request) {
        return service.create(new CreateCustomerCommand(
                values.get("email", String.class),
                values.get("displayName", String.class)
        ));
    }

    @PatchMapping("/{email}")
    @PreAuthorize("hasAuthority('SCOPE_customer.write')")
    @FieldContract(rejectUnknownJson = true)
    @Field(
            param = "email",
            source = FieldSource.PATH,
            required = true,
            style = "customerEmailStyle"
    )
    @Field(
            param = "displayName",
            source = FieldSource.JSON,
            nullable = true,
            style = "customerDisplayNameStyle"
    )
    @Field(param = "active", source = FieldSource.JSON, style = "booleanStyle")
    CustomerView update(FieldValues values, @Valid @RequestBody UpdateCustomerRequest request) {
        Boolean active = values.get("active", Boolean.class);
        return service.update(
                values.get("email", String.class),
                new UpdateCustomerCommand(
                        values.contains("displayName"),
                        values.get("displayName", String.class),
                        values.contains("active"),
                        Boolean.TRUE.equals(active)
                )
        );
    }

    @DeleteMapping("/{email}")
    @RawResponse
    @PreAuthorize("hasAuthority('SCOPE_customer.write')")
    @Field(
            param = "email",
            source = FieldSource.PATH,
            required = true,
            style = "customerEmailStyle"
    )
    ResponseEntity<Void> delete(FieldValues values) {
        service.delete(values.get("email", String.class));
        return ResponseEntity.noContent().build();
    }

    private static CustomerListQuery.SortField sortField(String value) {
        return switch (value == null ? "email" : value) {
            case "displayName" -> CustomerListQuery.SortField.DISPLAY_NAME;
            case "active" -> CustomerListQuery.SortField.ACTIVE;
            default -> CustomerListQuery.SortField.EMAIL;
        };
    }
}
