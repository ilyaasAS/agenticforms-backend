package com.agenticform.model.entity;

import java.time.Instant;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "form_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Access(AccessType.FIELD)
public class FormSession {

    @Id
    @Column(name = "session_id", length = 64, nullable = false)
    @Size(max = 64)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "form_id", nullable = false)
    @NotNull
    private Form form;

    @Column(name = "last_field_id")
    private Long lastFieldId;

    @Column(name = "answers_json", columnDefinition = "MEDIUMTEXT")
    private String answersJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @NotNull
    @Builder.Default
    private FormSessionStatus status = FormSessionStatus.IN_PROGRESS;

    /** E-mail vérifié sur la page Connexion (reprise inter-appareils). */
    @Column(name = "respondent_email", length = 320)
    private String respondentEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = FormSessionStatus.IN_PROGRESS;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
