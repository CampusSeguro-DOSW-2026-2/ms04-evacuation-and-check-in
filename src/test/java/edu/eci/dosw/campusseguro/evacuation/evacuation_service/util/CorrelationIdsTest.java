package edu.eci.dosw.campusseguro.evacuation.evacuation_service.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CorrelationIdsTest {

    private static final String VALIDO = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

    @Test
    void conservaUnUuidV4Valido() {
        assertEquals(VALIDO, CorrelationIds.resolve(VALIDO));
        assertEquals(VALIDO, CorrelationIds.resolve("  " + VALIDO + " "));
    }

    @Test
    void generaUnoNuevoSiFaltaOEsInvalido() {
        for (String recibido : new String[] { null, "", "no-es-uuid", "c19b472d-7a2a-1d3b-a12b-abcdef123456" }) {
            String resuelto = CorrelationIds.resolve(recibido);
            assertNotEquals(recibido, resuelto);
            assertEquals(4, UUID.fromString(resuelto).version());
        }
    }

    @Test
    void currentUsaElAtributoYSiNoLaCabecera() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIds.HEADER, VALIDO);
        assertEquals(VALIDO, CorrelationIds.current(request));

        request.setAttribute(CorrelationIds.ATTRIBUTE, "ya-resuelto");
        assertEquals("ya-resuelto", CorrelationIds.current(request));
        assertTrue(CorrelationIds.current(new MockHttpServletRequest()).length() == 36);
    }
}
