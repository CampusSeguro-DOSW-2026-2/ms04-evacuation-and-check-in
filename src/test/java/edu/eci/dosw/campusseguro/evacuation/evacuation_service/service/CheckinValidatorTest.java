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
 * Validación del cuerpo del check-in según el estado reportado (RN-MS04-03, RG-05)
 */
class CheckinValidatorTest {

    private static final UUID PUNTO = UUID.fromString("0b6f1d2e-3c4a-4b5d-8e9f-a1b2c3d4e5f6");
    private static final Ubicacion UBICACION = new Ubicacion(TipoUbicacion.EDIFICIO,
            UUID.fromString("5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a"));

    private static List<ErrorDetail> errores(CheckinRequest request) {
        ApiException e = assertThrows(ApiException.class, () -> CheckinValidator.validar(request));
        assertEquals(ErrorCode.VALIDATION, e.getCode());
        return e.getErrors();
    }

    @Test
    void sinEstadoOConUnEstadoFueraDelCatalogoPideUnEstadoValido() {
        assertEquals(List.of(new ErrorDetail(CheckinValidator.CAMPO_ESTADO, CheckinValidator.ESTADO_INVALIDO)),
                errores(new CheckinRequest(null, PUNTO, UBICACION)));
    }

    @Test
    void estoyASalvoExigeElPuntoDeEncuentro() {
        assertEquals(List.of(new ErrorDetail(CheckinValidator.CAMPO_PUNTO, CheckinValidator.FALTA_PUNTO)),
                errores(new CheckinRequest(EstadoCheckin.SAFE, null, null)));
    }

    @Test
    void estoyASalvoNoAdmiteUbicacion() {
        assertEquals(List.of(new ErrorDetail(CheckinValidator.CAMPO_UBICACION,
                CheckinValidator.UBICACION_SOLO_ASISTENCIA)),
                errores(new CheckinRequest(EstadoCheckin.SAFE, PUNTO, UBICACION)));
    }

    @Test
    void estoyASalvoConPuntoEsValido() {
        assertDoesNotThrow(() -> CheckinValidator.validar(new CheckinRequest(EstadoCheckin.SAFE, PUNTO, null)));
    }

    @Test
    void requieroAsistenciaExigeLaUbicacion() {
        assertEquals(List.of(new ErrorDetail(CheckinValidator.CAMPO_UBICACION, CheckinValidator.FALTA_UBICACION)),
                errores(new CheckinRequest(EstadoCheckin.NEEDS_ASSISTANCE, null, null)));
    }

    @Test
    void requieroAsistenciaExigeTipoEIdDeLaUbicacion() {
        List<ErrorDetail> errores = errores(new CheckinRequest(EstadoCheckin.NEEDS_ASSISTANCE, null,
                new Ubicacion(null, null)));
        assertEquals(List.of(
                new ErrorDetail(CheckinValidator.CAMPO_UBICACION_TIPO, CheckinValidator.FALTA_UBICACION),
                new ErrorDetail(CheckinValidator.CAMPO_UBICACION_ID, CheckinValidator.FALTA_LUGAR)), errores);
    }

    @Test
    void requieroAsistenciaNoAdmitePuntoDeEncuentro() {
        assertEquals(List.of(new ErrorDetail(CheckinValidator.CAMPO_PUNTO, CheckinValidator.PUNTO_SOLO_SAFE)),
                errores(new CheckinRequest(EstadoCheckin.NEEDS_ASSISTANCE, PUNTO, UBICACION)));
    }

    @Test
    void requieroAsistenciaConUbicacionEsValido() {
        assertDoesNotThrow(() -> CheckinValidator.validar(
                new CheckinRequest(EstadoCheckin.NEEDS_ASSISTANCE, null, UBICACION)));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoCheckin.class, names = { "EVACUATING", "OFF_CAMPUS" })
    void evacuandoYFueraDelCampusNoLlevanNiPuntoNiUbicacion(EstadoCheckin estado) {
        assertDoesNotThrow(() -> CheckinValidator.validar(new CheckinRequest(estado, null, null)));
        assertEquals(List.of(
                new ErrorDetail(CheckinValidator.CAMPO_PUNTO, CheckinValidator.PUNTO_SOLO_SAFE),
                new ErrorDetail(CheckinValidator.CAMPO_UBICACION, CheckinValidator.UBICACION_SOLO_ASISTENCIA)),
                errores(new CheckinRequest(estado, PUNTO, UBICACION)));
    }
}
