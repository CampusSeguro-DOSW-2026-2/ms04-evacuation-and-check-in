package edu.eci.dosw.campusseguro.evacuation.evacuation_service.util;

import java.util.ArrayList;
import java.util.List;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ErrorResponse.ErrorDetail;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;

/**
 * Validación de la paginación de los Estándares (sección 9): {@code page} desde 0 y {@code size} de 1 a 100, con
 * 20 por defecto. Los mensajes son los de los formularios F-02, F-03, F-04 y F-07
 */
public final class Paginacion {

    public static final String PAGINA_POR_DEFECTO = "0";
    public static final String TAMANO_POR_DEFECTO = "20";
    public static final int TAMANO_MAXIMO = 100;

    private Paginacion() {
    }

    /**
     * Valida los parámetros de paginación de un listado
     *
     * @param page número de página, desde 0
     * @param size tamaño de página, de 1 a 100
     * @throws ApiException 400 VALIDACION con un error por cada parámetro inválido
     */
    public static void validar(int page, int size) {
        List<ErrorDetail> errores = new ArrayList<>();
        if (page < 0) {
            errores.add(new ErrorDetail("page", "Página no válida."));
        }
        if (size < 1 || size > TAMANO_MAXIMO) {
            errores.add(new ErrorDetail("size", "Tamaño de página no válido (1 a 100)."));
        }
        if (!errores.isEmpty()) {
            throw ApiException.validacion(errores);
        }
    }
}
