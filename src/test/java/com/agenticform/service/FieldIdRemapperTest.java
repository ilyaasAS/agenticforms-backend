package com.agenticform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class FieldIdRemapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void remapsFieldIdsAndCanvasOrder() throws Exception {
        String json = """
                {"pages":[{"fieldIds":[10,20],"canvasOrder":["field:10","block:abc"]}]}
                """;

        String remapped = FieldIdRemapper.remap(objectMapper, json, Map.of(10L, 101L, 20L, 202L));
        JsonNode root = objectMapper.readTree(remapped);

        assertEquals(101L, root.path("pages").get(0).path("fieldIds").get(0).longValue());
        assertEquals(202L, root.path("pages").get(0).path("fieldIds").get(1).longValue());
        assertEquals("field:101", root.path("pages").get(0).path("canvasOrder").get(0).asText());
        assertEquals("block:abc", root.path("pages").get(0).path("canvasOrder").get(1).asText());
    }

    @Test
    void remapsStringFieldIdsInLogic() throws Exception {
        String json = """
                [{"conditions":[{"fieldId":"7","operator":"eq"}],"logic":{"refId":"7"}}]
                """;

        String remapped = FieldIdRemapper.remap(objectMapper, json, Map.of(7L, 70L));
        JsonNode root = objectMapper.readTree(remapped);

        assertEquals("70", root.get(0).path("conditions").get(0).path("fieldId").asText());
        assertEquals("70", root.get(0).path("logic").path("refId").asText());
    }

    @Test
    void leavesJsonUnchangedWhenMapIsEmpty() {
        String json = "{\"fieldIds\":[1]}";
        assertEquals(json, FieldIdRemapper.remap(objectMapper, json, Map.of()));
        assertNull(FieldIdRemapper.remap(objectMapper, null, Map.of(1L, 2L)));
    }
}
