package com.yourcaryourway.chat.accounts.domain;

import com.yourcaryourway.chat.accounts.UserRole;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Customer or agent account (subset of the target schema's AppUser entity). */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "user_role")
    private UserRole role = UserRole.CLIENT;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "account_status")
    private AccountStatus status = AccountStatus.PENDING_VERIFICATION;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "preferred_locale", nullable = false)
    private String preferredLocale = "en";

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(String email, String passwordHash, UserRole role, String firstName, String lastName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = AccountStatus.ACTIVE;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    /** "Camille D.": never the full name in the chat (data minimization). */
    public String displayName() {
        String initial = (lastName == null || lastName.isBlank()) ? "" : " " + lastName.charAt(0) + ".";
        return (firstName == null ? "Utilisateur" : firstName) + initial;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public UserRole getRole() { return role; }
    public AccountStatus getStatus() { return status; }
    public String getPreferredLocale() { return preferredLocale; }
}
