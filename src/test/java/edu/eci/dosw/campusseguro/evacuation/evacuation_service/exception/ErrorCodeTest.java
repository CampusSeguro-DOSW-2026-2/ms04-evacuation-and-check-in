package edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class ErrorCodeTest {

    /** Códigos del catálogo de los Estándares (sección 7.2) que puede generar un microservicio */
    private static final List<String> CATALOGO = List.of("VALIDACION", "NO_AUTENTICADO", "PROHIBIDO", "NO_ENCONTRADO",
            "METODO_NO_PERMITIDO", "CONFLICTO", "TIPO_NO_SOPORTADO", "REGLA_NEGOCIO", "ERROR_INTERNO");

    @Test
    void incluyeElCatalogoDeLosEstandaresMasLosPropios() {
        List<String> codigos = Arrays.stream(ErrorCode.values()).map(ErrorCode::getCode)
                .collect(Collectors.toList());

        assertTrue(codigos.containsAll(CATALOGO));
        assertTrue(codigos.contains("DEPENDENCIA_NO_DISPONIBLE"));
        assertEquals(503, ErrorCode.DEPENDENCY_UNAVAILABLE.getStatus());
    }

    @Test
    void losCodigosVanEnMayusculasConGuionBajo() {
        for (ErrorCode code : ErrorCode.values()) {
            assertTrue(code.getCode().matches("[A-Z]+(_[A-Z]+)*"), code.getCode());
        }
    }

    @Test
    void forStatusDevuelveElCodigoGeneralOUnoPorDefecto() {
        assertEquals(ErrorCode.NOT_FOUND, ErrorCode.forStatus(404));
        assertEquals(ErrorCode.CONFLICT, ErrorCode.forStatus(409));
        assertEquals(ErrorCode.INTERNAL_ERROR, ErrorCode.forStatus(599));
        assertEquals(ErrorCode.VALIDATION, ErrorCode.forStatus(418));
    }
}
