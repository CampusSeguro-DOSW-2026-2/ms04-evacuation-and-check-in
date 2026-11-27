package edu.eci.dosw.campusseguro.evacuation.evacuation_service.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;

/**
 * Minimal JSON writer used by the outgoing calls: maps, lists, simple values, escaping and unsupported values
 */
class JsonTest {

    /**
     * Writes maps, lists and simple values in the order of the map
     */
    @Test
    void writesMapsListsAndSimpleValues() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", 1);
        map.put("b", null);
        map.put("c", List.of("x", true));
        map.put("d", EstadoCheckin.SAFE);
        map.put("e", new BigDecimal("83.30"));

        assertEquals("{\"a\":1,\"b\":null,\"c\":[\"x\",true],\"d\":\"SAFE\",\"e\":83.30}", Json.write(map));
    }

    /**
     * Escapes quotes, backslashes, line breaks and control characters
     */
    @Test
    void escapesQuotesBackslashesAndLineBreaks() {
        assertEquals("\"a\\\"b\\\\c\\nd\\u0001\"", Json.write("a\"b\\c\nd\u0001"));
    }

    /**
     * Rejects types and numbers that JSON does not support
     */
    @Test
    void rejectsUnsupportedTypesAndNumbers() {
        assertThrows(IllegalArgumentException.class, () -> Json.write(new Object()));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Double.NaN));
    }
}
