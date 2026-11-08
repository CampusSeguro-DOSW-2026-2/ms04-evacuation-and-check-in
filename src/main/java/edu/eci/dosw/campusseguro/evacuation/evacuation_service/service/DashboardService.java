package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinResumen;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Consolidado;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Pendiente;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PersonaAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;

/**
 * Dashboard del evento: RF-MS04-06 Consolidar el estado del evento (Jira KAIR-577, historias KAIR-596 y KAIR-597),
 * RF-MS04-07 Identificar personas pendientes (KAIR-578, historias KAIR-598 y KAIR-599) y RF-MS04-08 Consultar
 * personas que requieren asistencia (KAIR-579, historia KAIR-600).
 *
 * Todas las consultas funcionan con el evento en cualquier estado (RN-MS04-22) y el BRIGADISTA ve solo lo de sus
 * zonas asignadas en MS05 (RN-MS04-14). Pendiente de implementar: responden 501 hasta que se complete la lógica
 */
@Service
public class DashboardService {

    /**
     * Consolidado de los estados vigentes del evento: total por cada uno de los cuatro estados (siempre los
     * cuatro, con 0 si no hay personas), personas a salvo por punto de encuentro y total de reportados
     * (RN-MS04-13). Usa {@code CheckinRepository#contarVigentesPorEstado} y {@code TotalesPorEstado#de}
     *
     * @param eventoId evento; 404 si no existe
     * @param requester solicitante con {@code ms04:dashboard:read}
     * @return el consolidado
     */
    public Consolidado consolidado(UUID eventoId, RequesterIdentity requester) {
        throw ApiException.noImplementado("RF-MS04-06 consolidado del evento");
    }

    /**
     * Estados vigentes del evento, con el nombre de cada persona (de MS01, RN-MS04-25), filtrados por estado y por
     * punto de encuentro. {@code puntoEncuentroId} solo se combina con SAFE o sin estado; si no, 400
     *
     * @param eventoId evento; 404 si no existe
     * @param estado filtro opcional por estado vigente
     * @param puntoEncuentroId filtro opcional por punto de encuentro (UUID de MS02)
     * @param page número de página, desde 0
     * @param size tamaño de página, de 1 a 100
     * @param requester solicitante con {@code ms04:dashboard:read}
     * @return la página de estados vigentes
     */
    public PageResponse<CheckinResumen> personas(UUID eventoId, EstadoCheckin estado, UUID puntoEncuentroId,
            int page, int size, RequesterIdentity requester) {
        throw ApiException.noImplementado("RF-MS04-06 personas por estado y punto de encuentro");
    }

    /**
     * Personas de la población objetivo (usuarios ACTIVOS de MS01 con un rol convocado) que no tienen ningún
     * check-in en el evento (RN-MS04-15). Si MS01 no entrega la población, 503 y el consolidado sigue disponible.
     * Para el BRIGADISTA la lista siempre es vacía (RN-MS04-14)
     *
     * @param eventoId evento; 404 si no existe
     * @param page número de página, desde 0
     * @param size tamaño de página, de 1 a 100
     * @param requester solicitante con {@code ms04:dashboard:read}
     * @return la página de pendientes
     */
    public PageResponse<Pendiente> pendientes(UUID eventoId, int page, int size, RequesterIdentity requester) {
        throw ApiException.noImplementado("RF-MS04-07 personas pendientes");
    }

    /**
     * Personas con estado vigente Requiero asistencia, del reporte más antiguo al más reciente, con su solicitud
     * en MS05 y el estado de su envío. MS04 solo muestra: la asignación y la atención ocurren en MS05
     * (RN-MS04-16)
     *
     * @param eventoId evento; 404 si no existe
     * @param page número de página, desde 0
     * @param size tamaño de página, de 1 a 100
     * @param requester solicitante con {@code ms04:dashboard:read}
     * @return la página de personas que requieren asistencia
     */
    public PageResponse<PersonaAsistencia> asistencia(UUID eventoId, int page, int size,
            RequesterIdentity requester) {
        throw ApiException.noImplementado("RF-MS04-08 personas que requieren asistencia");
    }
}
