package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.UsuarioNombresClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ZonasBrigadistaClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ZonasBrigadistaClient.ZonasAsignadas;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinResumen;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Consolidado;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Pendiente;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PersonaAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.TotalPorPunto;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.TotalesPorEstado;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository.ConteoPorEstadoYPunto;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Rol;

/**
 * Dashboard del evento: RF-MS04-06 Consolidar el estado del evento (Jira KAIR-577, historias KAIR-596 y KAIR-597),
 * RF-MS04-07 Identificar personas pendientes (KAIR-578, historias KAIR-598 y KAIR-599) y RF-MS04-08 Consultar
 * personas que requieren asistencia (KAIR-579, historia KAIR-600).
 *
 * Todas las consultas funcionan con el evento en cualquier estado (RN-MS04-22) y el BRIGADISTA ve solo lo de sus
 * zonas asignadas en MS05 (RN-MS04-14). Implementado: RF-MS04-06. Pendientes de implementar (responden 501 hasta
 * que se complete la lógica): RF-MS04-07 y RF-MS04-08
 */
@Service
public class DashboardService {

    public static final String EVENTO_NO_ENCONTRADO = "Evento no encontrado.";
    public static final String PUNTO_SOLO_SAFE = "El punto de encuentro solo aplica a Estoy a salvo.";

    private final EventoRepository eventos;
    private final CheckinRepository checkins;
    private final UsuarioNombresClient nombres;
    private final ZonasBrigadistaClient zonasBrigadista;
    private final Clock clock;

    /**
     * Alcance de lo que puede ver quien consulta: todo el evento, o solo las zonas de un BRIGADISTA
     *
     * @param restringido true si la consulta se limita a las zonas del brigadista
     * @param zonas zonas del brigadista; vacías cuando no hay restricción
     */
    private record Alcance(boolean restringido, ZonasAsignadas zonas) {
    }

    /**
     * Crea el service del dashboard con sus dependencias
     *
     * @param eventos repositorio de eventos
     * @param checkins repositorio de check-ins
     * @param nombres cliente de los nombres de MS01
     * @param zonasBrigadista cliente de las zonas de brigadista de MS05
     * @param clock reloj con el que se marca la fecha del cálculo
     */
    public DashboardService(EventoRepository eventos, CheckinRepository checkins, UsuarioNombresClient nombres,
            ZonasBrigadistaClient zonasBrigadista, Clock clock) {
        this.eventos = eventos;
        this.checkins = checkins;
        this.nombres = nombres;
        this.zonasBrigadista = zonasBrigadista;
        this.clock = clock;
    }

    /**
     * Consolidado de los estados vigentes del evento: total por cada uno de los cuatro estados (siempre los
     * cuatro, con 0 si no hay personas), personas a salvo por punto de encuentro y total de reportados
     * (RN-MS04-13). Para el BRIGADISTA cuenta solo lo de sus zonas y deja en 0 EVACUATING y OFF_CAMPUS
     * (RN-MS04-14). Funciona con el evento en cualquier estado (RN-MS04-22)
     *
     * @param eventoId evento
     * @param requester solicitante con {@code ms04:dashboard:read}
     * @return el consolidado
     * @throws ApiException 404 NO_ENCONTRADO si el evento no existe, 503 DEPENDENCIA_NO_DISPONIBLE si es un
     *         BRIGADISTA y MS05 no entrega sus zonas
     */
    public Consolidado consolidado(UUID eventoId, RequesterIdentity requester) {
        Evento evento = eventos.findById(eventoId)
                .orElseThrow(() -> ApiException.noEncontrado(EVENTO_NO_ENCONTRADO));
        Alcance alcance = alcanceDe(requester);
        List<ConteoPorEstadoYPunto> filas = checkins.contarVigentes(eventoId, alcance.restringido(),
                alcance.zonas().puntosEncuentro(), alcance.zonas().edificios(), alcance.zonas().espacios());

        Map<String, Long> conteos = new HashMap<>();
        List<TotalPorPunto> porPunto = new ArrayList<>();
        for (ConteoPorEstadoYPunto fila : filas) {
            conteos.merge(fila.getEstado(), fila.getTotal(), Long::sum);
            if (fila.getPunto() != null) {
                porPunto.add(new TotalPorPunto(fila.getPunto(), fila.getTotal()));
            }
        }
        TotalesPorEstado totales = TotalesPorEstado.de(conteos);
        return new Consolidado(evento.getId(), evento.getEstado(), totales.total(), totales, porPunto,
                Instant.now(clock).truncatedTo(ChronoUnit.MILLIS));
    }

    /**
     * Estados vigentes del evento, con el nombre de cada persona (de MS01, RN-MS04-25), filtrados por estado y por
     * punto de encuentro, del reporte más reciente al más antiguo. Si MS01 no responde, la lista sale con el
     * nombre nulo. Para el BRIGADISTA lista solo lo de sus zonas (RN-MS04-14)
     *
     * @param eventoId evento
     * @param estado filtro opcional por estado vigente
     * @param puntoEncuentroId filtro opcional por punto de encuentro (UUID de MS02)
     * @param page número de página, desde 0
     * @param size tamaño de página, de 1 a 100
     * @param requester solicitante con {@code ms04:dashboard:read}
     * @return la página de estados vigentes
     * @throws ApiException 400 VALIDACION si el punto de encuentro se combina con un estado distinto de SAFE, 404
     *         NO_ENCONTRADO si el evento no existe, 503 DEPENDENCIA_NO_DISPONIBLE si es un BRIGADISTA y MS05 no
     *         entrega sus zonas
     */
    public PageResponse<CheckinResumen> personas(UUID eventoId, EstadoCheckin estado, UUID puntoEncuentroId,
            int page, int size, RequesterIdentity requester) {
        if (puntoEncuentroId != null && estado != null && estado != EstadoCheckin.SAFE) {
            throw ApiException.validacion("punto_encuentro_id", PUNTO_SOLO_SAFE);
        }
        if (!eventos.existsById(eventoId)) {
            throw ApiException.noEncontrado(EVENTO_NO_ENCONTRADO);
        }
        Alcance alcance = alcanceDe(requester);
        Page<Checkin> pagina = checkins.buscarVigentes(eventoId, estado, puntoEncuentroId, alcance.restringido(),
                alcance.zonas().puntosEncuentro(), alcance.zonas().edificios(), alcance.zonas().espacios(),
                PageRequest.of(page, size));

        List<UUID> usuarios = pagina.getContent().stream().map(Checkin::getUsuarioId).toList();
        Map<UUID, String> nombresPorUsuario = nombres.nombresDe(usuarios);
        Page<CheckinResumen> resumenes = pagina.map(checkin -> new CheckinResumen(checkin.getUsuarioId(),
                nombresPorUsuario.get(checkin.getUsuarioId()), checkin.getEstado(), checkin.getPuntoEncuentroId(),
                checkin.getRegistradoEn()));
        return PageResponse.from(resumenes);
    }

    /**
     * Alcance de la consulta según los roles del solicitante: quien solo tiene el rol BRIGADISTA ve únicamente sus
     * zonas, que se piden a MS05; los demás roles con {@code ms04:dashboard:read} ven todo el evento
     *
     * @param requester solicitante con {@code ms04:dashboard:read}
     * @return el alcance de la consulta
     * @throws ApiException 503 DEPENDENCIA_NO_DISPONIBLE si es un BRIGADISTA y MS05 no entrega sus zonas
     */
    private Alcance alcanceDe(RequesterIdentity requester) {
        boolean soloBrigadista = requester.tieneRol(Rol.BRIGADISTA)
                && !requester.tieneRol(Rol.COORDINADOR)
                && !requester.tieneRol(Rol.ORGANIZADOR)
                && !requester.tieneRol(Rol.AUDITOR);
        if (soloBrigadista) {
            return new Alcance(true, zonasBrigadista.zonasDe(requester));
        }
        return new Alcance(false, new ZonasAsignadas(Set.of(), Set.of(), Set.of()));
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
