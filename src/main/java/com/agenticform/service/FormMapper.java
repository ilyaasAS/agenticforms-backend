package com.agenticform.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.agenticform.dto.CalculationDto;
import com.agenticform.dto.FieldSettingsDto;
import com.agenticform.dto.FormFieldResponse;
import com.agenticform.dto.FormPageDto;
import com.agenticform.dto.FormResponse;
import com.agenticform.dto.FormSummaryResponse;
import com.agenticform.dto.LogicRuleDto;
import com.agenticform.model.entity.FieldType;
import com.agenticform.model.entity.Form;
import com.agenticform.model.entity.FormField;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class FormMapper {

    private static final Logger log = LoggerFactory.getLogger(FormMapper.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<LogicRuleDto>> LOGIC_RULES = new TypeReference<>() {
    };
    private static final TypeReference<List<CalculationDto>> CALCULATIONS = new TypeReference<>() {
    };
    private static final TypeReference<List<FormPageDto>> PAGES = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public FormMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FormSummaryResponse toSummary(Form form) {
        int fieldCount = form.getFields() == null ? 0 : form.getFields().size();
        return new FormSummaryResponse(
                form.getId(),
                form.getWorkspace().getId(),
                form.getTitle(),
                form.getDescription(),
                form.getStatus().name(),
                fieldCount,
                form.getCreatedBy() != null ? form.getCreatedBy().getId() : null,
                form.getCreatedAt(),
                form.getUpdatedAt());
    }

    public FormSummaryResponse toSummary(Form form, int fieldCount) {
        return new FormSummaryResponse(
                form.getId(),
                form.getWorkspace().getId(),
                form.getTitle(),
                form.getDescription(),
                form.getStatus().name(),
                fieldCount,
                form.getCreatedBy() != null ? form.getCreatedBy().getId() : null,
                form.getCreatedAt(),
                form.getUpdatedAt());
    }

    public FormResponse toResponse(Form form) {
        List<FormFieldResponse> fields = form.getFields() == null
                ? List.of()
                : form.getFields().stream().map(this::toFieldResponse).toList();
        return new FormResponse(
                form.getId(),
                form.getWorkspace().getId(),
                form.getTitle(),
                form.getDescription(),
                form.getStatus().name(),
                form.getCreatedBy() != null ? form.getCreatedBy().getId() : null,
                fields,
                parseLogicRules(form.getLogicRulesJson()),
                parseCalculations(form.getCalculationsJson()),
                parsePages(form.getPagesJson()),
                form.getCreatedAt(),
                form.getUpdatedAt());
    }

    public String serializeLogicRules(List<LogicRuleDto> rules) {
        if (rules == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(rules);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Impossible de sérialiser logicRules.", ex);
        }
    }

    public List<LogicRuleDto> parseLogicRules(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<LogicRuleDto> parsed = objectMapper.readValue(json, LOGIC_RULES);
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (JsonProcessingException ex) {
            log.warn("logic_rules_json invalide, retour liste vide: {}", ex.getMessage());
            return List.of();
        }
    }

    public String serializeCalculations(List<CalculationDto> calculations) {
        if (calculations == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(calculations);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Impossible de sérialiser calculations.", ex);
        }
    }

    public List<CalculationDto> parseCalculations(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<CalculationDto> parsed = objectMapper.readValue(json, CALCULATIONS);
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (JsonProcessingException ex) {
            log.warn("calculations_json invalide, retour liste vide: {}", ex.getMessage());
            return List.of();
        }
    }

    public String serializePages(List<FormPageDto> pages) {
        if (pages == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(pages);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Impossible de sérialiser pages.", ex);
        }
    }

    public List<FormPageDto> parsePages(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<FormPageDto> parsed = objectMapper.readValue(json, PAGES);
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (JsonProcessingException ex) {
            log.warn("pages_json invalide, retour liste vide: {}", ex.getMessage());
            return List.of();
        }
    }

    public FormFieldResponse toFieldResponse(FormField field) {
        return new FormFieldResponse(
                field.getId(),
                field.getForm().getId(),
                field.getLabel(),
                field.getFieldType().name(),
                field.isRequired(),
                field.getDisplayOrder(),
                parseOptions(field.getOptionsJson()),
                field.getPlaceholder(),
                field.getUiComponent(),
                parseFieldSettings(field.getSettingsJson()),
                field.getCreatedAt(),
                field.getUpdatedAt());
    }

    public String serializeFieldSettings(FieldSettingsDto settings) {
        if (settings == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Impossible de sérialiser field settings.", ex);
        }
    }

    public FieldSettingsDto parseFieldSettings(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, FieldSettingsDto.class);
        } catch (JsonProcessingException ex) {
            log.warn("settings_json invalide, retour null: {}", ex.getMessage());
            return null;
        }
    }

    public String serializeOptions(List<String> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        List<String> cleaned = options.stream()
                .filter(o -> o != null && !o.isBlank())
                .map(String::trim)
                .toList();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(cleaned);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Impossible de sérialiser les options du champ.", ex);
        }
    }

    public List<String> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(optionsJson, STRING_LIST);
            return parsed == null ? List.of() : Collections.unmodifiableList(parsed);
        } catch (JsonProcessingException ex) {
            log.warn("options_json invalide, retour liste vide: {}", ex.getMessage());
            return List.of();
        }
    }

    public Map<Long, String> parseAnswerMap(String answersJson) {
        if (answersJson == null || answersJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(answersJson, new TypeReference<Map<String, Object>>() {
            });
            if (raw == null || raw.isEmpty()) {
                return Map.of();
            }
            Map<Long, String> result = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                try {
                    Long fieldId = Long.valueOf(entry.getKey());
                    Object value = entry.getValue();
                    if (value == null) {
                        continue;
                    }
                    String asText = String.valueOf(value).trim();
                    if (!asText.isEmpty() && !"null".equals(asText)) {
                        result.put(fieldId, asText);
                    }
                } catch (NumberFormatException ignored) {
                    // skip malformed keys
                }
            }
            return Collections.unmodifiableMap(result);
        } catch (Exception ex) {
            log.warn("answers_json invalide, retour map vide: {}", ex.getMessage());
            return Map.of();
        }
    }

    public boolean requiresOptions(FieldType fieldType) {
        return fieldType == FieldType.SINGLE_CHOICE
                || fieldType == FieldType.MULTIPLE_CHOICE
                || fieldType == FieldType.CHECKBOX
                || fieldType == FieldType.DROPDOWN
                || fieldType == FieldType.MULTISELECT
                || fieldType == FieldType.PICTURE_CHOICE
                || fieldType == FieldType.CHOICE_MATRIX
                || fieldType == FieldType.RANKING
                || fieldType == FieldType.SWITCH;
    }
}
