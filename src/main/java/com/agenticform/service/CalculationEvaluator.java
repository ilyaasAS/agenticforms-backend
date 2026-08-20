package com.agenticform.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.agenticform.dto.CalculationDto;
import com.agenticform.dto.CalculationRuleDto;
import com.agenticform.dto.LogicConditionDto;
import com.agenticform.dto.VisibilityNodeDto;

final class CalculationEvaluator {

    private static final Pattern ISO_DATE = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");
    private static final Pattern ISO_DATETIME =
            Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})[T ](\\d{1,2}):(\\d{2})");
    private static final Pattern TIME_ONLY = Pattern.compile("^(\\d{1,2}):(\\d{2})(?::\\d{2})?$");
    private static final Pattern SLASH_DATE =
            Pattern.compile("^(\\d{1,2})[/.](\\d{1,2})[/.](\\d{4})$");

    private CalculationEvaluator() {}

    static Map<String, String> evaluate(
            List<CalculationDto> calculations,
            Map<String, String> answers,
            Map<String, String> urlParams,
            Map<String, String> contactParams
    ) {
        Map<String, String> calcValues = new LinkedHashMap<>();
        if (calculations == null || calculations.isEmpty()) {
            return calcValues;
        }
        Map<String, String> url = urlParams != null ? urlParams : Map.of();
        Map<String, String> contact = contactParams != null ? contactParams : Map.of();
        for (CalculationDto calculation : calculations) {
            if (calculation == null || calculation.id() == null) {
                continue;
            }
            calcValues.put(
                    calculation.id(),
                    evaluateOne(calculation, answers, url, contact, calcValues)
            );
        }
        return calcValues;
    }

    private static String evaluateOne(
            CalculationDto calculation,
            Map<String, String> answers,
            Map<String, String> urlParams,
            Map<String, String> contactParams,
            Map<String, String> calcValues
    ) {
        String type = calculation.type() == null ? "text" : calculation.type();
        if ("duration".equalsIgnoreCase(type)) {
            return evaluateDuration(calculation, answers, urlParams, contactParams, calcValues);
        }
        String current = calculation.initialValue() == null ? "" : calculation.initialValue();
        List<CalculationRuleDto> rules = calculation.rules();
        if (rules == null) {
            return current;
        }
        for (CalculationRuleDto rule : rules) {
            if (rule == null || !ruleMatches(rule, answers, urlParams, contactParams, calcValues)) {
                continue;
            }
            String operand = rule.resultValue() == null ? "" : rule.resultValue();
            current = applyOperation(type, rule.operation(), current, operand);
        }
        return current;
    }

    private static boolean ruleMatches(
            CalculationRuleDto rule,
            Map<String, String> answers,
            Map<String, String> urlParams,
            Map<String, String> contactParams,
            Map<String, String> calcValues
    ) {
        if (Boolean.TRUE.equals(rule.always())) {
            return true;
        }
        VisibilityNodeDto logic = rule.logic();
        if (VisibilityLogicEvaluator.hasRules(logic)) {
            return VisibilityLogicEvaluator.evaluate(logic, answers, urlParams, contactParams, calcValues);
        }
        List<LogicConditionDto> conditions = rule.conditions();
        if (conditions == null || conditions.isEmpty()) {
            return false;
        }
        for (LogicConditionDto condition : conditions) {
            if (!legacyConditionMatches(condition, answers)) {
                return false;
            }
        }
        return true;
    }

    private static boolean legacyConditionMatches(LogicConditionDto condition, Map<String, String> answers) {
        if (condition == null || condition.fieldId() == null || condition.fieldId().isBlank()) {
            return false;
        }
        String left = answers.getOrDefault(condition.fieldId(), "").trim();
        String right = condition.value() == null ? "" : condition.value().trim();
        String operator = condition.operator() == null ? "equals" : condition.operator();
        String leftCmp = left.toLowerCase(Locale.ROOT);
        String rightCmp = right.toLowerCase(Locale.ROOT);
        return switch (operator) {
            case "is_empty" -> left.isEmpty();
            case "is_not_empty" -> !left.isEmpty();
            case "not_equals" -> !leftCmp.equals(rightCmp);
            case "contains" -> leftCmp.contains(rightCmp);
            case "equals" -> leftCmp.equals(rightCmp);
            default -> false;
        };
    }

    private static String applyOperation(String type, String operation, String current, String operand) {
        String op = operation == null || operation.isBlank() ? "assign" : operation;
        if ("text".equals(type) || "assign".equals(op)) {
            if ("add".equals(op) && "text".equals(type)) {
                return current + operand;
            }
            return operand;
        }
        double left = parseNumber(current);
        double right = parseNumber(operand);
        return switch (op) {
            case "add" -> String.valueOf(left + right);
            case "subtract" -> String.valueOf(left - right);
            case "multiply" -> String.valueOf(left * right);
            case "divide" -> right == 0 ? String.valueOf(left) : String.valueOf(left / right);
            default -> operand;
        };
    }

    private static String evaluateDuration(
            CalculationDto calculation,
            Map<String, String> answers,
            Map<String, String> urlParams,
            Map<String, String> contactParams,
            Map<String, String> calcValues
    ) {
        String startId = calculation.startFieldId() == null ? "" : calculation.startFieldId().trim();
        String endId = calculation.endFieldId() == null ? "" : calculation.endFieldId().trim();
        if (startId.isEmpty() || endId.isEmpty()) {
            return "";
        }
        LocalDateTime start = resolveDurationInstant(
                calculation.startRefKind(), startId, answers, urlParams, contactParams, calcValues);
        LocalDateTime end = resolveDurationInstant(
                calculation.endRefKind(), endId, answers, urlParams, contactParams, calcValues);
        if (start == null || end == null) {
            return "";
        }
        String units = calculation.units() == null ? "days" : calculation.units().trim().toLowerCase(Locale.ROOT);
        long value = switch (units) {
            case "minutes" -> ChronoUnit.MINUTES.between(start, end);
            case "hours" -> ChronoUnit.HOURS.between(start, end);
            case "weeks" -> ChronoUnit.WEEKS.between(start, end);
            case "months" -> ChronoUnit.MONTHS.between(start, end);
            case "years" -> ChronoUnit.YEARS.between(start, end);
            default -> ChronoUnit.DAYS.between(start, end);
        };
        return Long.toString(value);
    }

    private static LocalDateTime resolveDurationInstant(
            String kind,
            String refId,
            Map<String, String> answers,
            Map<String, String> urlParams,
            Map<String, String> contactParams,
            Map<String, String> calcValues
    ) {
        String normalized = kind == null ? "field" : kind.trim().toLowerCase(Locale.ROOT);
        if ("datetime".equals(normalized)) {
            return fromDatetimeRef(refId);
        }
        Map<String, String> source = switch (normalized) {
            case "url" -> urlParams;
            case "contact" -> contactParams;
            case "calculation" -> calcValues;
            default -> answers;
        };
        if (source == null) {
            return null;
        }
        return parseInstant(source.get(refId));
    }

    private static LocalDateTime fromDatetimeRef(String refId) {
        if (refId == null || refId.isBlank()) {
            return null;
        }
        String id = refId.trim();
        LocalDate today = LocalDate.now();
        return switch (id) {
            case "today", "date.today" -> today.atStartOfDay();
            case "yesterday", "date.yesterday" -> today.minusDays(1).atStartOfDay();
            case "tomorrow", "date.tomorrow" -> today.plusDays(1).atStartOfDay();
            case "n7" -> today.plusDays(7).atStartOfDay();
            case "n30" -> today.plusDays(30).atStartOfDay();
            case "now", "date.now" -> LocalDateTime.now().withSecond(0).withNano(0);
            default -> {
                if (id.startsWith("date.plus_days:")) {
                    try {
                        long days = Long.parseLong(id.substring("date.plus_days:".length()));
                        yield today.plusDays(days).atStartOfDay();
                    } catch (NumberFormatException ex) {
                        yield null;
                    }
                }
                yield null;
            }
        };
    }

    private static LocalDateTime parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        Matcher isoDate = ISO_DATE.matcher(value);
        if (isoDate.matches()) {
            try {
                return LocalDate.parse(value).atStartOfDay();
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
        Matcher isoDateTime = ISO_DATETIME.matcher(value);
        if (isoDateTime.find()) {
            try {
                int hour = Integer.parseInt(isoDateTime.group(4));
                int minute = Integer.parseInt(isoDateTime.group(5));
                LocalDate date = LocalDate.parse(isoDateTime.group(1) + "-" + isoDateTime.group(2) + "-" + isoDateTime.group(3));
                return LocalDateTime.of(date, LocalTime.of(hour, minute));
            } catch (RuntimeException ex) {
                return null;
            }
        }
        Matcher timeOnly = TIME_ONLY.matcher(value);
        if (timeOnly.matches()) {
            try {
                int hour = Integer.parseInt(timeOnly.group(1));
                int minute = Integer.parseInt(timeOnly.group(2));
                return LocalDateTime.of(LocalDate.of(1970, 1, 1), LocalTime.of(hour, minute));
            } catch (RuntimeException ex) {
                return null;
            }
        }
        Matcher slash = SLASH_DATE.matcher(value);
        if (slash.matches()) {
            try {
                int first = Integer.parseInt(slash.group(1));
                int second = Integer.parseInt(slash.group(2));
                int year = Integer.parseInt(slash.group(3));
                int month = first;
                int day = second;
                if (first > 12) {
                    day = first;
                    month = second;
                }
                return LocalDate.of(year, month, day).atStartOfDay();
            } catch (RuntimeException ex) {
                return null;
            }
        }
        return null;
    }

    private static double parseNumber(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
