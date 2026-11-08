package edu.eci.dosw.campusseguro.evacuation.evacuation_service.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;

class JsonTest {

    @Test
    void escribeMapasListasYValoresSimples() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("a", 1);
        mapa.put("b", null);
        mapa.put("c", List.of("x", true));
        mapa.put("d", EstadoCheckin.SAFE);
        mapa.put("e", new BigDecimal("83.30"));

        assertEquals("{\"a\":1,\"b\":null,\"c\":[\"x\",true],\"d\":\"SAFE\",\"e\":83.30}", Json.write(mapa));
    }

    @Test
    void escapaComillasBarrasYSaltosDeLinea() {
        assertEquals("\"a\\\"b\\\\c\\nd\\u0001\"", Json.write("a\"b\\c\nd\u0001"));
    }

    @Test
    void rechazaTiposYNumerosNoSoportados() {
        assertThrows(IllegalArgumentException.class, () -> Json.write(new Object()));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Double.NaN));
    }
}
