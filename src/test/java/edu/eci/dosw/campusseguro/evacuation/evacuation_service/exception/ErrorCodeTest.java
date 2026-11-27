package edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Error codes of MS04: the catalog of the integration standards plus the codes of the service
 */
class ErrorCodeTest {

    /** Codes of the catalog of the standards (section 7.2) that a microservice can generate */
    private static final List<String> CATALOG = List.of("VALIDACION", "NO_AUTENTICADO", "PROHIBIDO", "NO_ENCONTRADO",
            "METODO_NO_PERMITIDO", "CONFLICTO", "TIPO_NO_SOPORTADO", "REGLA_NEGOCIO", "ERROR_INTERNO");

    /**
     * The codes include the catalog of the standards and the unavailable dependency
     */
    @Test
    void includesTheCatalogOfTheStandardsAndItsOwnCodes() {
        List<String> codes = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getCode)
                .toList();

        assertTrue(codes.containsAll(CATALOG));
        assertTrue(codes.contains("DEPENDENCIA_NO_DISPONIBLE"));
        assertEquals(503, ErrorCode.DEPENDENCY_UNAVAILABLE.getStatus());
    }

    /**
     * The codes are written in upper case with underscores
     */
    @Test
    void theCodesAreUpperCaseWithUnderscores() {
        for (ErrorCode code : ErrorCode.values()) {
            assertTrue(code.getCode().matches("[A-Z]+(_[A-Z]+)*"), code.getCode());
        }
    }

    /**
     * A status returns its general code, or a default one by family
     */
    @Test
    void forStatusReturnsTheGeneralCodeOrADefaultOne() {
        assertEquals(ErrorCode.NOT_FOUND, ErrorCode.forStatus(404));
        assertEquals(ErrorCode.CONFLICT, ErrorCode.forStatus(409));
        assertEquals(ErrorCode.INTERNAL_ERROR, ErrorCode.forStatus(599));
        assertEquals(ErrorCode.VALIDATION, ErrorCode.forStatus(418));
    }
}
