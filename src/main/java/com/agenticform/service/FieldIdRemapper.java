package com.agenticform.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Réécrit les identifiants de champs dans un JSON (pages, logique, calculs, settings)
 * après duplication d’un formulaire.
 */
final class FieldIdRemapper {

    private static final Set<String> ID_KEYS = Set.of("fieldId", "startFieldId", "endFieldId", "refId");

    private FieldIdRemapper() {
    }

    static String remap(ObjectMapper objectMapper, String json, Map<Long, Long> fieldIdMap) {
        if (json == null || json.isBlank() || fieldIdMap == null || fieldIdMap.isEmpty()) {
            return json;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || root.isNull()) {
                return json;
            }
            remapNode(root, fieldIdMap);
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            return json;
        }
    }

    private static void remapNode(JsonNode node, Map<Long, Long> fieldIdMap) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<String> names = object.fieldNames();
            ArrayList<String> keys = new ArrayList<>();
            names.forEachRemaining(keys::add);
            for (String key : keys) {
                JsonNode value = object.get(key);
                if (ID_KEYS.contains(key)) {
                    applyMappedId(object, key, value, fieldIdMap);
                } else if ("fieldIds".equals(key) && value != null && value.isArray()) {
                    remapIdArray((ArrayNode) value, fieldIdMap);
                } else if ("canvasOrder".equals(key) && value != null && value.isArray()) {
                    remapCanvasOrder((ArrayNode) value, fieldIdMap);
                } else {
                    remapNode(value, fieldIdMap);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                remapNode(child, fieldIdMap);
            }
        }
    }

    private static void remapIdArray(ArrayNode array, Map<Long, Long> fieldIdMap) {
        for (int i = 0; i < array.size(); i++) {
            JsonNode value = array.get(i);
            Long mapped = mappedId(value, fieldIdMap);
            if (mapped == null) {
                continue;
            }
            if (value != null && value.isTextual()) {
                array.set(i, String.valueOf(mapped));
            } else {
                array.set(i, mapped);
            }
        }
    }

    private static void remapCanvasOrder(ArrayNode array, Map<Long, Long> fieldIdMap) {
        for (int i = 0; i < array.size(); i++) {
            JsonNode value = array.get(i);
            if (value == null || !value.isTextual()) {
                continue;
            }
            String text = value.asText();
            if (!text.startsWith("field:")) {
                continue;
            }
            Long mapped = mappedIdFromText(text.substring("field:".length()), fieldIdMap);
            if (mapped != null) {
                array.set(i, "field:" + mapped);
            }
        }
    }

    private static void applyMappedId(
            ObjectNode object,
            String key,
            JsonNode value,
            Map<Long, Long> fieldIdMap) {
        Long mapped = mappedId(value, fieldIdMap);
        if (mapped == null) {
            return;
        }
        if (value != null && value.isNumber()) {
            object.put(key, mapped);
        } else {
            object.put(key, String.valueOf(mapped));
        }
    }

    private static Long mappedId(JsonNode value, Map<Long, Long> fieldIdMap) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return fieldIdMap.get(value.longValue());
        }
        if (value.isTextual()) {
            return mappedIdFromText(value.asText(), fieldIdMap);
        }
        return null;
    }

    private static Long mappedIdFromText(String text, Map<Long, Long> fieldIdMap) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return fieldIdMap.get(Long.parseLong(text.trim()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
