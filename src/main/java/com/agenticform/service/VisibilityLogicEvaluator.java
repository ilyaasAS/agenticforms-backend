package com.agenticform.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.agenticform.dto.FieldSettingsDto;
import com.agenticform.dto.VisibilityNodeDto;

final class VisibilityLogicEvaluator {

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter TIME_FR =
            DateTimeFormatter.ofPattern("HH:mm");

    private VisibilityLogicEvaluator() {}

    static boolean isFieldVisible(
            FieldSettingsDto settings,
            Map<String, String> answers,
            Map<String, String> urlParams
    ) {
        return isFieldVisible(settings, answers, urlParams, Map.of());
    }

    static boolean isFieldVisible(
            FieldSettingsDto settings,
            Map<String, String> answers,
            Map<String, String> urlParams,
            Map<String, String> contactParams
    ) {
        return isFieldVisible(settings, answers, urlParams, contactParams, Map.of());
    }

    static boolean isFieldVisible(
            FieldSettingsDto settings,
            Map<String, String> answers,
            Map<String, String> urlParams,
            Map<String, String> contactParams,
            Map<String, String> calcValues
    ) {
        if (settings == null) {
            return true;
        }
        if (Boolean.TRUE.equals(settings.hideAlways())) {
            return false;
        }
        VisibilityNodeDto logic = settings.visibilityLogic();
        if (logic == null || !hasRules(logic)) {
            return true;
        }
        Map<String, String> contact = contactParams != null ? contactParams : Map.of();
        Map<String, String> calcs = calcValues != null ? calcValues : Map.of();
        boolean matched = evaluate(logic, answers, urlParams, contact, calcs);
        if ("hide_when".equals(settings.hideMode())) {
            return !matched;
        }
        return matched;
    }

    static boolean hasRules(VisibilityNodeDto node) {
        if (node == null) {
            return false;
        }
        if ("condition".equals(node.type())) {
            return node.refId() != null && !node.refId().isBlank();
        }
        List<VisibilityNodeDto> children = node.children();
        if (children == null || children.isEmpty()) {
            return false;
        }
        for (VisibilityNodeDto child : children) {
            if (hasRules(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean evaluate(
            VisibilityNodeDto node,
            Map<String, String> answers,
            Map<String, String> urlParams
    ) {
        return evaluate(node, answers, urlParams, Map.of(), Map.of());
    }

    static boolean evaluate(
            VisibilityNodeDto node,
            Map<String, String> answers,
            Map<String, String> urlParams,
            Map<String, String> contactParams
    ) {
        return evaluate(node, answers, urlParams, contactParams, Map.of());
    }

    static boolean evaluate(
            VisibilityNodeDto node,
            Map<String, String> answers,
            Map<String, String> urlParams,
            Map<String, String> contactParams,
            Map<String, String> calcValues
    ) {
        if (node == null) {
            return true;
        }
        if ("condition".equals(node.type())) {
            return evaluateCondition(node, answers, urlParams, contactParams, calcValues);
        }
        List<VisibilityNodeDto> children = node.children();
        if (children == null || children.isEmpty()) {
            return true;
        }
        boolean or = "or".equalsIgnoreCase(node.join());
        if (or) {
            for (VisibilityNodeDto child : children) {
                if (evaluate(child, answers, urlParams, contactParams, calcValues)) {
                    return true;
                }
            }
            return false;
        }
        for (VisibilityNodeDto child : children) {
            if (!evaluate(child, answers, urlParams, contactParams, calcValues)) {
                return false;
            }
        }
        return true;
    }

    private static boolean evaluateCondition(
            VisibilityNodeDto node,
            Map<String, String> answers,
            Map<String, String> urlParams
    ) {
        return evaluateCondition(node, answers, urlParams, Map.of(), Map.of());
    }

    private static boolean evaluateCondition(
            VisibilityNodeDto node,
            Map<String, String> answers,
            Map<String, String> urlParams,
            Map<String, String> contactParams
    ) {
        return evaluateCondition(node, answers, urlParams, contactParams, Map.of());
    }

    private static boolean evaluateCondition(
            VisibilityNodeDto node,
            Map<String, String> answers,
            Map<String, String> urlParams,
            Map<String, String> contactParams,
            Map<String, String> calcValues
    ) {
        String left = resolve(node.refKind(), node.refId(), answers, urlParams, contactParams, calcValues).trim();
        String right = node.value() == null ? "" : node.value().trim();
        String operator = node.operator() == null ? "" : node.operator().trim();
        if (node.refId() == null || node.refId().isBlank() || operator.isEmpty()) {
            return false;
        }
        String leftCmp = left.toLowerCase(Locale.ROOT);
        String rightCmp = right.toLowerCase(Locale.ROOT);
        return switch (operator) {
            case "is_empty" -> left.isEmpty();
            case "is_not_empty" -> !left.isEmpty();
            case "not_equals" -> !leftCmp.equals(rightCmp);
            case "contains" -> !rightCmp.isEmpty() && leftCmp.contains(rightCmp);
            case "not_contains" -> rightCmp.isEmpty() || !leftCmp.contains(rightCmp);
            case "equals" -> leftCmp.equals(rightCmp);
            default -> false;
        };
    }

    private static String resolve(
            String kind,
            String refId,
            Map<String, String> answers,
            Map<String, String> urlParams
    ) {
        return resolve(kind, refId, answers, urlParams, Map.of(), Map.of());
    }

    private static String resolve(
            String kind,
            String refId,
            Map<String, String> answers,
            Map<String, String> urlParams,
            Map<String, String> contactParams
    ) {
        return resolve(kind, refId, answers, urlParams, contactParams, Map.of());
    }

    private static String resolve(
            String kind,
            String refId,
            Map<String, String> answers,
            Map<String, String> urlParams,
            Map<String, String> contactParams,
            Map<String, String> calcValues
    ) {
        if (refId == null || refId.isBlank()) {
            return "";
        }
        if ("url".equals(kind)) {
            return urlParams.getOrDefault(refId, "");
        }
        if ("contact".equals(kind)) {
            return contactParams.getOrDefault(refId, "");
        }
        if ("calculation".equals(kind)) {
            return calcValues.getOrDefault(refId, "");
        }
        if ("datetime".equals(kind)) {
            LocalDate today = LocalDate.now();
            return switch (refId) {
                case "today", "date.today" -> today.format(DATE_FR);
                case "yesterday", "date.yesterday" -> today.minusDays(1).format(DATE_FR);
                case "tomorrow", "date.tomorrow" -> today.plusDays(1).format(DATE_FR);
                case "n7" -> today.plusDays(7).format(DATE_FR);
                case "n30" -> today.plusDays(30).format(DATE_FR);
                case "now", "date.now" -> LocalTime.now().format(TIME_FR);
                default -> "";
            };
        }
        if ("field".equals(kind) || kind == null || kind.isBlank()) {
            String value = answers.get(refId);
            return value == null ? "" : value;
        }
        return "";
    }
}
