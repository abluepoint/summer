package com.abluepoint.summer.sample.identity;

import com.abluepoint.summer.data.jpa.entity.BaseLongEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "summer_auth_user", uniqueConstraints = @UniqueConstraint(name = "uk_summer_auth_user_username", columnNames = "username"))
class AuthUser extends BaseLongEntity {
    @Column(nullable = false, length = 128)
    private String username;
    @Column(nullable = false, length = 255)
    private String passwordHash;
    @Column(nullable = false, length = 32)
    private String role;
    @Column(nullable = false)
    private boolean active = true;

    protected AuthUser() {}
    AuthUser(String username, String passwordHash, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }
    String username() { return username; }
    String passwordHash() { return passwordHash; }
    String role() { return role; }
    boolean active() { return active; }
    void active(boolean value) { active = value; }
}
