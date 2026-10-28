package com.abluepoint.summer.sample.customer.model.entity;

import com.abluepoint.summer.data.jpa.persistence.api.BaseLongEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity(name = "Customer")
@Table(name = "summer_customer", uniqueConstraints = {
        @UniqueConstraint(name = "uk_summer_customer_username", columnNames = "username"),
        @UniqueConstraint(name = "uk_summer_customer_email", columnNames = "email")
})
public class CustomerEntity extends BaseLongEntity {
    @Column(nullable = false, length = 64)
    private String username;
    @Column(nullable = false, length = 200)
    private String email;
    @Column(nullable = false, length = 200)
    private String displayName;
    @Column(nullable = false)
    private boolean active = true;
    protected CustomerEntity() {}
    public CustomerEntity(String username, String email, String displayName) { this.username = username; this.email = email; this.displayName = displayName; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public boolean isActive() { return active; }
    public void changeEmail(String email) { this.email = email; }
    public void changeDisplayName(String displayName) { this.displayName = displayName; }
    public void changeActive(boolean active) { this.active = active; }
}
