package com.agenticform.model.entity;

import java.time.Instant;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "scheduling_reminders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Access(AccessType.FIELD)
public class SchedulingReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_scheduling_reminder")
    private Long id;

    @Column(name = "guest_email", nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String guestEmail;

    @Column(name = "guest_name", length = 255)
    @Size(max = 255)
    private String guestName;

    @Column(nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String title;

    @Column(name = "start_label", nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String startLabel;

    @Column(name = "html_link", length = 1024)
    private String htmlLink;

    @Column(name = "remind_at", nullable = false)
    @NotNull
    private Instant remindAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
