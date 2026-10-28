package com.abluepoint.summer.sample.identity.infrastructure.persistence;

import com.abluepoint.summer.data.jpa.persistence.api.BaseLongEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity(name = "AuthUser")
@Table(name = "summer_auth_user", uniqueConstraints = @UniqueConstraint(name = "uk_summer_auth_user_username", columnNames = "username"))
public class AuthUserEntity extends BaseLongEntity {
    @Column(nullable = false, length = 128)
    private String username;
    @Column(nullable = false, length = 255)
    private String passwordHash;
    @Column(nullable = false, length = 32)
    private String role;
    @Column(nullable = false)
    private boolean active = true;

    protected AuthUserEntity() {}
    public AuthUserEntity(String username, String passwordHash, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }
    public String username() { return username; }
    public String passwordHash() { return passwordHash; }
    public String role() { return role; }
    public boolean active() { return active; }
    public void active(boolean value) { active = value; }
}
