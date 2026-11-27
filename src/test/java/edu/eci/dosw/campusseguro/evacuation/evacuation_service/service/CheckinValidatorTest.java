package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinRequest;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ErrorResponse.ErrorDetail;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Ubicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;

/**
 * Validation of the body of the check-in according to the reported state (RN-MS04-03, RG-05)
 */
class CheckinValidatorTest {

    private static final UUID POINT = UUID.fromString("0b6f1d2e-3c4a-4b5d-8e9f-a1b2c3d4e5f6");
    private static final Ubicacion LOCATION = new Ubicacion(TipoUbicacion.EDIFICIO,
            UUID.fromString("5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a"));

    /**
     * Validates a body that must be rejected and returns its errors
     *
     * @param request body of the check-in
     * @return the errors of the 400 answer
     */
    private static List<ErrorDetail> errorsOf(CheckinRequest request) {
        ApiException error = assertThrows(ApiException.class, () -> CheckinValidator.validar(request));
        assertEquals(ErrorCode.VALIDATION, error.getCode());
        return error.getErrors();
    }

    /**
     * Without state, or with a state outside the catalog, a valid state is requested
     */
    @Test
    void withoutStateAValidStateIsRequested() {
        assertEquals(List.of(new ErrorDetail(CheckinValidator.CAMPO_ESTADO, CheckinValidator.ESTADO_INVALIDO)),
                errorsOf(new CheckinRequest(null, POINT, LOCATION)));
        assertEquals(List.of(new ErrorDetail(CheckinValidator.CAMPO_ESTADO, CheckinValidator.ESTADO_INVALIDO)),
                errorsOf(null));
    }

    /**
     * SAFE requires the meeting point
     */
    @Test
    void safeRequiresTheMeetingPoint() {
        assertEquals(List.of(new ErrorDetail(CheckinValidator.CAMPO_PUNTO, CheckinValidator.FALTA_PUNTO)),
                errorsOf(new CheckinRequest(EstadoCheckin.SAFE, null, null)));
    }

    /**
     * SAFE does not accept a location
     */
    @Test
    void safeDoesNotAcceptALocation() {
        assertEquals(List.of(new ErrorDetail(CheckinValidator.CAMPO_UBICACION,
                CheckinValidator.UBICACION_SOLO_ASISTENCIA)),
                errorsOf(new CheckinRequest(EstadoCheckin.SAFE, POINT, LOCATION)));
    }

    /**
     * SAFE with a meeting point is valid
     */
    @Test
    void safeWithPointIsValid() {
        assertDoesNotThrow(() -> CheckinValidator.validar(new CheckinRequest(EstadoCheckin.SAFE, POINT, null)));
    }

    /**
     * NEEDS_ASSISTANCE requires the location
     */
    @Test
    void needsAssistanceRequiresTheLocation() {
        assertEquals(List.of(new ErrorDetail(CheckinValidator.CAMPO_UBICACION, CheckinValidator.FALTA_UBICACION)),
                errorsOf(new CheckinRequest(EstadoCheckin.NEEDS_ASSISTANCE, null, null)));
    }

    /**
     * NEEDS_ASSISTANCE requires the type and the id of the location
     */
    @Test
    void needsAssistanceRequiresTypeAndIdOfTheLocation() {
        List<ErrorDetail> errors = errorsOf(new CheckinRequest(EstadoCheckin.NEEDS_ASSISTANCE, null,
                new Ubicacion(null, null)));
        assertEquals(List.of(
                new ErrorDetail(CheckinValidator.CAMPO_UBICACION_TIPO, CheckinValidator.FALTA_UBICACION),
                new ErrorDetail(CheckinValidator.CAMPO_UBICACION_ID, CheckinValidator.FALTA_LUGAR)), errors);
    }

    /**
     * NEEDS_ASSISTANCE does not accept a meeting point
     */
    @Test
    void needsAssistanceDoesNotAcceptAMeetingPoint() {
        assertEquals(List.of(new ErrorDetail(CheckinValidator.CAMPO_PUNTO, CheckinValidator.PUNTO_SOLO_SAFE)),
                errorsOf(new CheckinRequest(EstadoCheckin.NEEDS_ASSISTANCE, POINT, LOCATION)));
    }

    /**
     * NEEDS_ASSISTANCE with a location is valid
     */
    @Test
    void needsAssistanceWithLocationIsValid() {
        assertDoesNotThrow(() -> CheckinValidator.validar(
                new CheckinRequest(EstadoCheckin.NEEDS_ASSISTANCE, null, LOCATION)));
    }

    /**
     * EVACUATING and OFF_CAMPUS carry neither meeting point nor location
     *
     * @param state reported state
     */
    @ParameterizedTest
    @EnumSource(value = EstadoCheckin.class, names = { "EVACUATING", "OFF_CAMPUS" })
    void evacuatingAndOffCampusCarryNeitherPointNorLocation(EstadoCheckin state) {
        assertDoesNotThrow(() -> CheckinValidator.validar(new CheckinRequest(state, null, null)));
        assertEquals(List.of(
                new ErrorDetail(CheckinValidator.CAMPO_PUNTO, CheckinValidator.PUNTO_SOLO_SAFE),
                new ErrorDetail(CheckinValidator.CAMPO_UBICACION, CheckinValidator.UBICACION_SOLO_ASISTENCIA)),
                errorsOf(new CheckinRequest(state, POINT, LOCATION)));
    }
}
