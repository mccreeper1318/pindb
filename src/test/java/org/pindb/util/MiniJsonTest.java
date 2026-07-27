package org.pindb.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MiniJsonTest {
    @Test
    void roundTripsObjectsAndArrays() {
        Map<String, Object> source = Map.of(
                "name", "PinDB",
                "enabled", true,
                "numbers", List.of(1, 2, 3),
                "nested", Map.of("value", "quoted \"text\"")
        );
        Object parsed = MiniJson.parse(MiniJson.stringify(source));
        assertEquals("PinDB", MiniJson.string(MiniJson.object(parsed).get("name")));
        assertEquals(3, MiniJson.array(MiniJson.object(parsed).get("numbers")).size());
    }
}
