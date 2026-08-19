package com.abluepoint.summer.sample.customer;

import com.abluepoint.summer.data.jpa.entity.BaseLongEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "summer_customer")
class Customer extends BaseLongEntity {

    @Column(nullable = false, unique = true, length = 200)
    private String email;

    @Column(nullable = false, length = 200)
    private String displayName;

    @Column(nullable = false)
    private boolean active = true;

    protected Customer() {
    }

    Customer(String email, String displayName) {
        this.email = email;
        this.displayName = displayName;
    }

    String getEmail() {
        return email;
    }

    String getDisplayName() {
        return displayName;
    }

    boolean isActive() {
        return active;
    }
}
