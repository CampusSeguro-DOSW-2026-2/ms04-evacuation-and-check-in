package edu.eci.dosw.campusseguro.evacuation.evacuation_service.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Correlation id of each request: a valid UUID v4 is kept and any other value is replaced by a new one
 */
class CorrelationIdsTest {

    private static final String VALID = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

    /**
     * A valid UUID v4 is kept without the lateral spaces
     */
    @Test
    void keepsAValidUuidV4() {
        assertEquals(VALID, CorrelationIds.resolve(VALID));
        assertEquals(VALID, CorrelationIds.resolve("  " + VALID + " "));
    }

    /**
     * A new UUID v4 is generated when the value is missing or invalid
     */
    @Test
    void generatesANewOneWhenMissingOrInvalid() {
        for (String received : new String[] { null, "", "no-es-uuid", "c19b472d-7a2a-1d3b-a12b-abcdef123456" }) {
            String resolved = CorrelationIds.resolve(received);
            assertNotEquals(received, resolved);
            assertEquals(4, UUID.fromString(resolved).version());
        }
    }

    /**
     * The current id is the attribute of the request and, without it, the header
     */
    @Test
    void currentUsesTheAttributeAndOtherwiseTheHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIds.HEADER, VALID);
        assertEquals(VALID, CorrelationIds.current(request));

        request.setAttribute(CorrelationIds.ATTRIBUTE, "ya-resuelto");
        assertEquals("ya-resuelto", CorrelationIds.current(request));
        assertEquals(36, CorrelationIds.current(new MockHttpServletRequest()).length());
    }
}
