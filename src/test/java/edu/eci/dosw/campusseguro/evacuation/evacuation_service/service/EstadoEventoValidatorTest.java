package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ErrorResponse.ErrorDetail;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Escenario;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EstadoEventoRequest;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.EstadoEventoValidator.EstadoValidado;

/**
 * Validation of the body that MS06 sends with the state of the event (F-05, RN-MS04-24): every field with the
 * message of the form, the normalization of the texts and the dates, the format of the convened roles and the
 * gathering of every error in a single answer
 */
class EstadoEventoValidatorTest {

    private static final UUID EVENT = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID OTHER_EVENT = UUID.fromString("9c3e5a1d-2b4f-4c6e-8a1b-3d5f7e9a1c24");

    /**
     * Valid body of a scheduled drill
     *
     * @param state state of the event
     * @param startDate start date, or null
     * @param roles convened roles
     * @return the body
     */
    private static EstadoEventoRequest body(EstadoEvento state, String startDate, List<String> roles) {
        return new EstadoEventoRequest(EVENT, "  Simulacro bloque B  ", true, state, startDate,
                new Escenario(List.of(" Bloque B "), List.of("Zona norte")), roles);
    }

    /**
     * Validates a body that must be rejected and returns its errors
     *
     * @param request body to validate
     * @return the errors of the 400 answer
     */
    private static List<ErrorDetail> errorsOf(EstadoEventoRequest request) {
        ApiException error = assertThrows(ApiException.class, () -> EstadoEventoValidator.validar(EVENT, request));
        assertEquals(ErrorCode.VALIDATION, error.getCode());
        return error.getErrors();
    }

    /**
     * A valid body is normalized: texts without lateral spaces, the date in UTC and the roles in the received order
     */
    @Test
    void aValidBodyIsNormalized() {
        EstadoValidado data = EstadoEventoValidator.validar(EVENT, body(EstadoEvento.PROGRAMADO,
                "2026-11-25T10:00:00-05:00", List.of("PROFESOR", "ESTUDIANTE")));

        assertEquals("Simulacro bloque B", data.nombre());
        assertTrue(data.simulacro());
        assertEquals(EstadoEvento.PROGRAMADO, data.estado());
        assertEquals(Instant.parse("2026-11-25T15:00:00Z"), data.fechaInicio());
        assertEquals(List.of("Bloque B"), data.edificios());
        assertEquals(List.of("Zona norte"), data.zonas());
        assertEquals(List.of("PROFESOR", "ESTUDIANTE"), data.roles());
    }

    /**
     * Without body the event is not valid
     */
    @Test
    void withoutBodyTheEventIsNotValid() {
        assertEquals(List.of(new ErrorDetail(EstadoEventoValidator.CAMPO_EVENTO_ID,
                EstadoEventoValidator.EVENTO_NO_VALIDO)), errorsOf(null));
    }

    /**
     * The id of the body is required and must be the id of the route
     */
    @Test
    void theIdOfTheBodyMustBeTheOneOfTheRoute() {
        EstadoEventoRequest withoutId = new EstadoEventoRequest(null, "Simulacro", true, EstadoEvento.BORRADOR, null,
                null, List.of("ESTUDIANTE"));
        EstadoEventoRequest otherId = new EstadoEventoRequest(OTHER_EVENT, "Simulacro", true, EstadoEvento.BORRADOR,
                null, null, List.of("ESTUDIANTE"));

        assertEquals(List.of(new ErrorDetail("eventoId", EstadoEventoValidator.EVENTO_NO_VALIDO)),
                errorsOf(withoutId));
        assertEquals(List.of(new ErrorDetail("eventoId", EstadoEventoValidator.EVENTO_DISTINTO_DE_LA_RUTA)),
                errorsOf(otherId));
    }

    /**
     * The name is required and has from 1 to 150 characters after removing the lateral spaces
     */
    @Test
    void theNameHasFromOneToOneHundredFiftyCharacters() {
        String longest = "N".repeat(150);
        EstadoValidado data = EstadoEventoValidator.validar(EVENT, new EstadoEventoRequest(EVENT, longest, true,
                EstadoEvento.BORRADOR, null, null, List.of("ESTUDIANTE")));
        assertEquals(longest, data.nombre());

        for (String name : Arrays.asList(null, "   ", "N".repeat(151))) {
            EstadoEventoRequest request = new EstadoEventoRequest(EVENT, name, true, EstadoEvento.BORRADOR, null, null,
                    List.of("ESTUDIANTE"));
            assertEquals(List.of(new ErrorDetail("nombre", EstadoEventoValidator.NOMBRE_NO_VALIDO)),
                    errorsOf(request));
        }
    }

    /**
     * The type of event and a state of the catalog are required
     */
    @Test
    void theTypeAndTheStateAreRequired() {
        EstadoEventoRequest request = new EstadoEventoRequest(EVENT, "Simulacro", null, null, null, null,
                List.of("ESTUDIANTE"));

        assertEquals(List.of(new ErrorDetail("simulacro", EstadoEventoValidator.FALTA_SIMULACRO),
                new ErrorDetail("estado", EstadoEventoValidator.ESTADO_NO_VALIDO)), errorsOf(request));
    }

    /**
     * The start date is required to schedule, is not sent with a draft and must have an offset
     */
    @Test
    void theStartDateDependsOnTheState() {
        List<String> roles = List.of("ESTUDIANTE");

        assertEquals(List.of(new ErrorDetail("fechaInicio", EstadoEventoValidator.FALTA_FECHA)),
                errorsOf(body(EstadoEvento.PROGRAMADO, " ", roles)));
        assertEquals(List.of(new ErrorDetail("fechaInicio", EstadoEventoValidator.FECHA_EN_BORRADOR)),
                errorsOf(body(EstadoEvento.BORRADOR, "2026-11-25T10:00:00Z", roles)));
        assertEquals(List.of(new ErrorDetail("fechaInicio", EstadoEventoValidator.FECHA_NO_VALIDA)),
                errorsOf(body(EstadoEvento.EN_EJECUCION, "2026-11-25T10:00:00", roles)));
        assertNull(EstadoEventoValidator.validar(EVENT, body(EstadoEvento.EN_EJECUCION, null, roles)).fechaInicio());
    }

    /**
     * Without scenario the lists are empty, and every building and zone has from 1 to 100 characters
     */
    @Test
    void theScenarioTextsHaveFromOneToOneHundredCharacters() {
        EstadoValidado withoutScenario = EstadoEventoValidator.validar(EVENT, new EstadoEventoRequest(EVENT,
                "Simulacro", true, EstadoEvento.BORRADOR, null, null, List.of("ESTUDIANTE")));
        assertEquals(List.of(), withoutScenario.edificios());
        assertEquals(List.of(), withoutScenario.zonas());

        EstadoEventoRequest invalid = new EstadoEventoRequest(EVENT, "Simulacro", true, EstadoEvento.BORRADOR, null,
                new Escenario(List.of("B".repeat(101)), List.of("   ")), List.of("ESTUDIANTE"));
        assertEquals(List.of(new ErrorDetail("escenario.edificios", EstadoEventoValidator.EDIFICIO_NO_VALIDO),
                new ErrorDetail("escenario.zonas", EstadoEventoValidator.ZONA_NO_VALIDA)), errorsOf(invalid));
    }

    /**
     * At least one role is convened
     */
    @Test
    void atLeastOneRoleIsConvened() {
        assertEquals(List.of(new ErrorDetail("rolesConvocados", EstadoEventoValidator.FALTAN_ROLES)),
                errorsOf(body(EstadoEvento.BORRADOR, null, null)));
        assertEquals(List.of(new ErrorDetail("rolesConvocados", EstadoEventoValidator.FALTAN_ROLES)),
                errorsOf(body(EstadoEvento.BORRADOR, null, List.of())));
    }

    /**
     * Any role with the role format is accepted, also one created by the administrator, up to 30 characters
     *
     * @param role convened role
     */
    @ParameterizedTest
    @ValueSource(strings = { "ESTUDIANTE", "VOLUNTARIO", "BRIGADA_2", "AB", "ABCDEFGHIJKLMNOPQRSTUVWXYZ_123" })
    void aRoleWithTheRoleFormatIsAccepted(String role) {
        assertEquals(List.of(role), EstadoEventoValidator.validar(EVENT, body(EstadoEvento.BORRADOR, null,
                List.of(role))).roles());
    }

    /**
     * A role without the role format, an empty one or a repeated one is rejected
     *
     * @param roles convened roles separated by commas
     */
    @ParameterizedTest
    @ValueSource(strings = { "estudiante", "A", "2ESTUDIANTE", "ESTUDIANTE-1", "ABCDEFGHIJKLMNOPQRSTUVWXYZ_1234",
            "ESTUDIANTE,ESTUDIANTE" })
    void aRoleWithoutTheFormatOrRepeatedIsRejected(String roles) {
        assertEquals(List.of(new ErrorDetail("rolesConvocados", EstadoEventoValidator.ROL_NO_VALIDO)),
                errorsOf(body(EstadoEvento.BORRADOR, null, List.of(roles.split(",")))));
    }

    /**
     * A null role in the list is rejected
     */
    @Test
    void aNullRoleIsRejected() {
        List<String> roles = new ArrayList<>();
        roles.add("ESTUDIANTE");
        roles.add(null);

        assertEquals(List.of(new ErrorDetail("rolesConvocados", EstadoEventoValidator.ROL_NO_VALIDO)),
                errorsOf(body(EstadoEvento.BORRADOR, null, roles)));
    }

    /**
     * Every invalid field is reported in the same answer
     */
    @Test
    void everyErrorIsReportedTogether() {
        EstadoEventoRequest request = new EstadoEventoRequest(OTHER_EVENT, "", null, EstadoEvento.PROGRAMADO, null,
                new Escenario(List.of(""), List.of()), List.of());

        assertEquals(6, errorsOf(request).size());
    }
}
