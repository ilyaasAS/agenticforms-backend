package com.agenticform.model.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
@Table(name = "forms")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Access(AccessType.FIELD)
public class Form {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_form")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    @NotNull
    private Workspace workspace;

    @Column(nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @NotNull
    @Builder.Default
    private FormStatus status = FormStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @OneToMany(mappedBy = "form", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<FormField> fields = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private long viewCount = 0;

    /** JSON array of page-branching logic rules. */
    @Column(name = "logic_rules_json", columnDefinition = "TEXT")
    private String logicRulesJson;

    /** JSON array of calculated variables. */
    @Column(name = "calculations_json", columnDefinition = "TEXT")
    private String calculationsJson;

    /** JSON array of page definitions (id, type, title, fieldIds…). */
    @Column(name = "pages_json", columnDefinition = "MEDIUMTEXT")
    private String pagesJson;

    /** Thème UI (ex. light, dark, crimson, quiet-sands…). */
    @Column(name = "theme_id", nullable = false, length = 32)
    @NotBlank
    @Size(max = 32)
    @Builder.Default
    private String themeId = "dark";

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
            status = FormStatus.DRAFT;
        }
        if (themeId == null || themeId.isBlank()) {
            themeId = "dark";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addField(FormField field) {
        fields.add(field);
        field.setForm(this);
    }

    public void removeField(FormField field) {
        fields.remove(field);
        field.setForm(null);
    }
}
