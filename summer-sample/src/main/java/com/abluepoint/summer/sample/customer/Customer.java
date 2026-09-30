package com.abluepoint.summer.sample.customer;

import com.abluepoint.summer.data.jpa.entity.BaseLongEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "summer_customer", uniqueConstraints = {
        @UniqueConstraint(name = "uk_summer_customer_username", columnNames = "username"),
        @UniqueConstraint(name = "uk_summer_customer_email", columnNames = "email")
})
class Customer extends BaseLongEntity {
    @Column(nullable = false, length = 64)
    private String username;
    @Column(nullable = false, length = 200)
    private String email;
    @Column(nullable = false, length = 200)
    private String displayName;
    @Column(nullable = false)
    private boolean active = true;
    protected Customer() {}
    Customer(String username, String email, String displayName) { this.username = username; this.email = email; this.displayName = displayName; }
    String getUsername() { return username; }
    String getEmail() { return email; }
    String getDisplayName() { return displayName; }
    boolean isActive() { return active; }
    void changeEmail(String email) { this.email = email; }
    void changeDisplayName(String displayName) { this.displayName = displayName; }
    void changeActive(boolean active) { this.active = active; }
}
