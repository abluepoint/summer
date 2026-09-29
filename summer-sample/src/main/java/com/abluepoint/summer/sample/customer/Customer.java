package com.abluepoint.summer.sample.customer;

import com.abluepoint.summer.data.jpa.entity.BaseLongEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "summer_customer")
class Customer extends BaseLongEntity {
    @Column(nullable = false, unique = true, length = 64)
    private String username;
    @Column(nullable = false, unique = true, length = 200)
    private String email;
    @Column(nullable = false, length = 200)
    private String displayName;
    @Column(nullable = false)
    private boolean active = true;
    protected Customer() {}
    Customer(String username, String email, String displayName) { this.username = username; this.email = email; this.displayName = displayName; }
    /** Compatibility constructor for persistence fixtures; username is derived from the legacy first value. */
    Customer(String email, String displayName) { this(email.substring(0, email.indexOf('@') > 0 ? email.indexOf('@') : email.length()), email, displayName); }
    String getUsername() { return username; }
    String getEmail() { return email; }
    String getDisplayName() { return displayName; }
    boolean isActive() { return active; }
    void changeEmail(String email) { this.email = email; }
    void changeDisplayName(String displayName) { this.displayName = displayName; }
    void changeActive(boolean active) { this.active = active; }
}
