package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.util.ArrayList;
import java.util.List;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinRequest;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ErrorResponse.ErrorDetail;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Ubicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;

/**
 * Valida el cuerpo del reporte de estado según el estado reportado (F-01, RN-MS04-03, RN-MS04-04, RN-MS04-09 y
 * RG-05): el punto de encuentro es obligatorio con SAFE y prohibido con los demás estados; la ubicación es
 * obligatoria con NEEDS_ASSISTANCE y prohibida con los demás. Los mensajes son los del formulario F-01
 *
 * Valida en el service y no con Bean Validation en el controller para que cada intento fallido quede auditado
 * (RF-MS04-02, A1)
 */
public final class CheckinValidator {

    public static final String CAMPO_ESTADO = "estado";
    public static final String CAMPO_PUNTO = "punto_encuentro_id";
    public static final String CAMPO_UBICACION = "ubicacion";
    public static final String CAMPO_UBICACION_TIPO = "ubicacion.tipo";
    public static final String CAMPO_UBICACION_ID = "ubicacion.id";

    public static final String ESTADO_INVALIDO = "Seleccione un estado válido.";
    public static final String FALTA_PUNTO = "Seleccione el punto de encuentro donde está.";
    public static final String PUNTO_SOLO_SAFE = "El punto de encuentro solo aplica a Estoy a salvo.";
    public static final String FALTA_UBICACION = "Indique dónde necesita la asistencia.";
    public static final String FALTA_LUGAR = "Seleccione el lugar donde necesita la asistencia.";
    public static final String UBICACION_SOLO_ASISTENCIA = "La ubicación solo aplica a Requiero asistencia.";

    private CheckinValidator() {
    }

    /**
     * Valida el cuerpo del check-in
     *
     * @param request cuerpo recibido; un estado fuera del catálogo llega como null
     * @throws ApiException 400 VALIDACION con un error por cada campo inválido
     */
    public static void validar(CheckinRequest request) {
        List<ErrorDetail> errores = new ArrayList<>();
        EstadoCheckin estado = request == null ? null : request.estado();
        if (estado == null) {
            errores.add(new ErrorDetail(CAMPO_ESTADO, ESTADO_INVALIDO));
            throw ApiException.validacion(errores);
        }
        boolean conPunto = request.puntoEncuentroId() != null;
        Ubicacion ubicacion = request.ubicacion();
        switch (estado) {
            case SAFE -> {
                if (!conPunto) {
                    errores.add(new ErrorDetail(CAMPO_PUNTO, FALTA_PUNTO));
                }
                if (ubicacion != null) {
                    errores.add(new ErrorDetail(CAMPO_UBICACION, UBICACION_SOLO_ASISTENCIA));
                }
            }
            case NEEDS_ASSISTANCE -> {
                if (ubicacion == null) {
                    errores.add(new ErrorDetail(CAMPO_UBICACION, FALTA_UBICACION));
                } else {
                    if (ubicacion.tipo() == null) {
                        errores.add(new ErrorDetail(CAMPO_UBICACION_TIPO, FALTA_UBICACION));
                    }
                    if (ubicacion.id() == null) {
                        errores.add(new ErrorDetail(CAMPO_UBICACION_ID, FALTA_LUGAR));
                    }
                }
                if (conPunto) {
                    errores.add(new ErrorDetail(CAMPO_PUNTO, PUNTO_SOLO_SAFE));
                }
            }
            case EVACUATING, OFF_CAMPUS -> {
                if (conPunto) {
                    errores.add(new ErrorDetail(CAMPO_PUNTO, PUNTO_SOLO_SAFE));
                }
                if (ubicacion != null) {
                    errores.add(new ErrorDetail(CAMPO_UBICACION, UBICACION_SOLO_ASISTENCIA));
                }
            }
        }
        if (!errores.isEmpty()) {
            throw ApiException.validacion(errores);
        }
    }
}
