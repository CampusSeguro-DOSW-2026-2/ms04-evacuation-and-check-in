package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.PoblacionObjetivoClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.UsuarioNombresClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ZonasBrigadistaClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ZonasBrigadistaClient.ZonasAsignadas;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinResumen;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Consolidado;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Pendiente;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PersonaAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.SolicitudAsistenciaResumen;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.TotalPorPunto;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.TotalesPorEstado;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Ubicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.SolicitudAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository.ConteoPorEstadoYPunto;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.SolicitudAsistenciaRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Rol;

/**
 * Dashboard del evento: RF-MS04-06 Consolidar el estado del evento (Jira KAIR-577, historias KAIR-596 y KAIR-597),
 * RF-MS04-07 Identificar personas pendientes (KAIR-578, historias KAIR-598 y KAIR-599) y RF-MS04-08 Consultar
 * personas que requieren asistencia (KAIR-579, historia KAIR-600).
 *
 * Todas las consultas funcionan con el evento en cualquier estado (RN-MS04-22) y el BRIGADISTA ve solo lo de sus
 * zonas asignadas en MS05 (RN-MS04-14). Implementados: RF-MS04-06, RF-MS04-07 y RF-MS04-08
 */
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    public static final String EVENTO_NO_ENCONTRADO = "Evento no encontrado.";
    public static final String PUNTO_SOLO_SAFE = "El punto de encuentro solo aplica a Estoy a salvo.";
    public static final String PENDIENTES_NO_DISPONIBLES = "La lista de pendientes no está disponible en este momento.";

    private final EventoRepository eventos;
    private final CheckinRepository checkins;
    private final SolicitudAsistenciaRepository solicitudes;
    private final UsuarioNombresClient nombres;
    private final ZonasBrigadistaClient zonasBrigadista;
    private final PoblacionObjetivoClient poblacionObjetivo;
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
     * @param solicitudes repositorio de las solicitudes de asistencia
     * @param nombres cliente de los nombres de MS01
     * @param zonasBrigadista cliente de las zonas de brigadista de MS05
     * @param poblacionObjetivo cliente de la población objetivo de MS01
     * @param clock reloj con el que se marca la fecha del cálculo y se fija el corte de la población
     */
    public DashboardService(EventoRepository eventos, CheckinRepository checkins,
            SolicitudAsistenciaRepository solicitudes, UsuarioNombresClient nombres,
            ZonasBrigadistaClient zonasBrigadista, PoblacionObjetivoClient poblacionObjetivo, Clock clock) {
        this.eventos = eventos;
        this.checkins = checkins;
        this.solicitudes = solicitudes;
        this.nombres = nombres;
        this.zonasBrigadista = zonasBrigadista;
        this.poblacionObjetivo = poblacionObjetivo;
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
        if (esSoloBrigadista(requester)) {
            return new Alcance(true, zonasBrigadista.zonasDe(requester));
        }
        return new Alcance(false, new ZonasAsignadas(Set.of(), Set.of(), Set.of()));
    }

    /**
     * Indica si el solicitante tiene el rol BRIGADISTA y ninguno de los roles que ven todo el evento
     *
     * @param requester solicitante con {@code ms04:dashboard:read}
     * @return true si su consulta se limita a lo que le muestra el BRIGADISTA (RN-MS04-14)
     */
    private boolean esSoloBrigadista(RequesterIdentity requester) {
        return requester.tieneRol(Rol.BRIGADISTA)
                && !requester.tieneRol(Rol.COORDINADOR)
                && !requester.tieneRol(Rol.ORGANIZADOR)
                && !requester.tieneRol(Rol.AUDITOR);
    }

    /**
     * Personas de la población objetivo (usuarios ACTIVOS de MS01 con un rol convocado) que no tienen ningún
     * check-in en el evento (RN-MS04-15), ordenadas por id de usuario, con su nombre (de MS01, RN-MS04-25). Si MS01
     * no entrega la población, 503 y el consolidado sigue disponible; si no entrega los nombres, la lista sale con
     * el nombre nulo. Para el BRIGADISTA la lista siempre es vacía y no se consulta a MS01 (RN-MS04-14). Funciona
     * con el evento en cualquier estado (RN-MS04-22)
     *
     * @param eventoId evento
     * @param page número de página, desde 0
     * @param size tamaño de página, de 1 a 100
     * @param requester solicitante con {@code ms04:dashboard:read}
     * @return la página de pendientes, con el total de pendientes en {@code total_elementos}
     * @throws ApiException 404 NO_ENCONTRADO si el evento no existe, 503 DEPENDENCIA_NO_DISPONIBLE si MS01 no
     *         entrega la población objetivo
     */
    public PageResponse<Pendiente> pendientes(UUID eventoId, int page, int size, RequesterIdentity requester) {
        Evento evento = eventos.findById(eventoId)
                .orElseThrow(() -> ApiException.noEncontrado(EVENTO_NO_ENCONTRADO));
        if (esSoloBrigadista(requester)) {
            return PageResponse.of(List.of(), page, size, 0);
        }

        Set<UUID> poblacion = poblacionObjetivo
                .usuariosActivosPorRoles(evento.getRolesConvocados(), evento.corteDePoblacion(Instant.now(clock)),
                        requester)
                .orElseThrow(() -> ApiException.dependenciaNoDisponible(PENDIENTES_NO_DISPONIBLES));
        Set<UUID> reportados = Set.copyOf(checkins.buscarUsuariosConEstadoVigente(eventoId));
        List<UUID> pendientes = poblacion.stream()
                .filter(usuarioId -> !reportados.contains(usuarioId))
                .sorted(Comparator.comparing(UUID::toString))
                .toList();

        int desde = (int) Math.min((long) page * size, pendientes.size());
        int hasta = (int) Math.min((long) desde + size, pendientes.size());
        List<UUID> usuarios = pendientes.subList(desde, hasta);
        Map<UUID, String> nombresPorUsuario = nombres.nombresDe(usuarios);
        List<Pendiente> pagina = usuarios.stream()
                .map(usuarioId -> new Pendiente(usuarioId, nombresPorUsuario.get(usuarioId)))
                .toList();
        return PageResponse.of(pagina, page, size, pendientes.size());
    }

    /**
     * Personas con estado vigente Requiero asistencia, del reporte más antiguo al más reciente, con su ubicación,
     * la fecha de su check-in vigente, su nombre (de MS01, RN-MS04-25) y su solicitud en MS05 con el estado de su
     * envío. MS04 solo muestra: la asignación y la atención ocurren en MS05 (RN-MS04-16). Quien cambia a otro
     * estado deja de aparecer. Para el BRIGADISTA lista solo las personas cuya ubicación está en sus zonas
     * (RN-MS04-14). Si MS01 no responde, la lista sale con el nombre nulo. Funciona con el evento en cualquier
     * estado (RN-MS04-22)
     *
     * @param eventoId evento
     * @param page número de página, desde 0
     * @param size tamaño de página, de 1 a 100
     * @param requester solicitante con {@code ms04:dashboard:read}
     * @return la página de personas que requieren asistencia
     * @throws ApiException 404 NO_ENCONTRADO si el evento no existe, 503 DEPENDENCIA_NO_DISPONIBLE si es un
     *         BRIGADISTA y MS05 no entrega sus zonas
     */
    public PageResponse<PersonaAsistencia> asistencia(UUID eventoId, int page, int size,
            RequesterIdentity requester) {
        if (!eventos.existsById(eventoId)) {
            throw ApiException.noEncontrado(EVENTO_NO_ENCONTRADO);
        }
        Alcance alcance = alcanceDe(requester);
        Page<Checkin> pagina = checkins.buscarVigentesConAsistencia(eventoId, alcance.restringido(),
                alcance.zonas().puntosEncuentro(), alcance.zonas().edificios(), alcance.zonas().espacios(),
                PageRequest.of(page, size));

        List<UUID> usuarios = pagina.getContent().stream().map(Checkin::getUsuarioId).toList();
        Map<UUID, String> nombresPorUsuario = nombres.nombresDe(usuarios);
        Map<UUID, SolicitudAsistencia> solicitudPorUsuario = new HashMap<>();
        if (!usuarios.isEmpty()) {
            for (SolicitudAsistencia solicitud : solicitudes.findByEventoIdAndUsuarioIdIn(eventoId, usuarios)) {
                solicitudPorUsuario.put(solicitud.getUsuarioId(), solicitud);
            }
        }

        List<PersonaAsistencia> personas = new ArrayList<>();
        int sinSolicitud = 0;
        for (Checkin checkin : pagina.getContent()) {
            SolicitudAsistenciaResumen resumen = null;
            SolicitudAsistencia solicitud = solicitudPorUsuario.get(checkin.getUsuarioId());
            if (solicitud == null) {
                sinSolicitud++;
            } else {
                resumen = new SolicitudAsistenciaResumen(solicitud.getSolicitudMs05Id(), solicitud.getEstadoEnvio());
            }
            personas.add(new PersonaAsistencia(checkin.getUsuarioId(), nombresPorUsuario.get(checkin.getUsuarioId()),
                    new Ubicacion(checkin.getUbicacionTipo(), checkin.getUbicacionId()), checkin.getRegistradoEn(),
                    resumen));
        }
        if (sinSolicitud > 0) {
            log.warn("{} personas con Requiero asistencia vigente del evento {} no tienen solicitud de asistencia",
                    sinSolicitud, eventoId);
        }
        return PageResponse.of(personas, page, size, pagina.getTotalElements());
    }
}
