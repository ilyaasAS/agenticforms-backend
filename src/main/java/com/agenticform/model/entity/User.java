package com.agenticform.model.entity;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Access(AccessType.FIELD)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_user")
    private Long id;

    @Column(nullable = false, length = 255)
    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    /** Never serialize the hash in API responses (defense in depth). */
    @JsonIgnore
    @Column(nullable = false, length = 255)
    @NotBlank
    @Size(min = 16, max = 255)
    private String password;

    @Column(name = "full_name", length = 255)
    @Size(max = 255)
    private String fullName;

    /**
     * True si l'utilisateur peut se connecter avec email/mot de passe
     * (inscription locale). False pour les comptes créés uniquement via OAuth.
     */
    @Column(name = "password_enabled", nullable = false)
    private boolean passwordEnabled = true;

    /**
     * Incrémenté au logout / reset password pour invalider les JWT existants (claim {@code tv}).
     */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    /**
     * True après vérification e-mail (lien d'inscription) ou création via OAuth IdP vérifié.
     */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserOAuthAccount> oauthAccounts = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @NotNull
    private Role role = Role.ROLE_USER;

    /** Compte bloqué par un admin : plus de login ni de session JWT. */
    @Column(nullable = false)
    @Builder.Default
    private boolean blocked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (role == null) {
            role = Role.ROLE_USER;
        }
    }

    public void addOAuthAccount(UserOAuthAccount account) {
        oauthAccounts.add(account);
        account.setUser(this);
    }

    public void removeOAuthAccount(UserOAuthAccount account) {
        oauthAccounts.remove(account);
        account.setUser(null);
    }

    public void markEmailVerified() {
        this.emailVerified = true;
        if (this.emailVerifiedAt == null) {
            this.emailVerifiedAt = LocalDateTime.now();
        }
    }
}
