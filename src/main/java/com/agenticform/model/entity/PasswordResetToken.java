package com.agenticform.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "password_reset_tokens", uniqueConstraints = {
        @UniqueConstraint(name = "uk_password_reset_token_hash", columnNames = "token_hash")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reset_token")
    private Long id;

    /** SHA-256 hex du token brut envoyé par e-mail (jamais le secret en clair). */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    @NotBlank
    @Size(min = 64, max = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull
    private User user;

    @Column(name = "expiry_date", nullable = false)
    @NotNull
    private LocalDateTime expiryDate;

    @PrePersist
    void onCreate() {
        if (expiryDate == null) {
            expiryDate = LocalDateTime.now().plusMinutes(30);
        }
    }

    public boolean isExpired() {
        return expiryDate == null || expiryDate.isBefore(LocalDateTime.now());
    }
}
