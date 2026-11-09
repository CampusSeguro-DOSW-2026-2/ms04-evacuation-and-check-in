package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.PoblacionObjetivoClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PrimerCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Resultados;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.SolicitudAsistenciaResumen;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.SolicitudResultado;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.TotalPorPunto;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.TotalesPorEstado;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEnvio;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;

/**
 * RF-MS04-10 Entregar resultados del evento (Jira KAIR-583, historia KAIR-605). MS04 entrega datos y no calcula
 * indicadores ni compara simulacros (RN-MS04-18): la evaluación es de MS06 y los indicadores de MS08
 *
 * Orden de la consulta:
 * 1. Busca el evento (404 si no existe)
 * 2. Pide a MS01 la población objetivo, fuera de la transacción para no retener una conexión de la base durante
 *    la llamada HTTP. Si MS01 falla, la población y los pendientes quedan en null y el resto se entrega (A2)
 * 3. Lee todo lo demás en una sola transacción de solo lectura REPEATABLE READ: todas las consultas ven la misma
 *    foto de la base, así que los totales cuadran entre sí aunque lleguen check-ins mientras tanto
 */
@Service
public class ResultadosService {

    static final String EVENTO_NO_ENCONTRADO = "Evento no encontrado.";

    private final EventoRepository eventos;
    private final CheckinRepository checkins;
    private final PoblacionObjetivoClient poblacionObjetivo;
    private final Clock clock;
    private final TransactionTemplate lecturaConsistente;

    /**
     * @param eventos repositorio de eventos
     * @param checkins repositorio de check-ins
     * @param poblacionObjetivo cliente de MS01 para la población objetivo
     * @param clock reloj de la aplicación
     * @param transactionManager administrador de transacciones
     */
    public ResultadosService(EventoRepository eventos, CheckinRepository checkins,
            PoblacionObjetivoClient poblacionObjetivo, Clock clock, PlatformTransactionManager transactionManager) {
        this.eventos = eventos;
        this.checkins = checkins;
        this.poblacionObjetivo = poblacionObjetivo;
        this.clock = clock;
        this.lecturaConsistente = new TransactionTemplate(transactionManager);
        this.lecturaConsistente.setReadOnly(true);
        this.lecturaConsistente.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    /**
     * Resultados de check-in del evento: población objetivo, total que reportó y pendientes, totales por estado y
     * por punto de encuentro, personas que reportaron Requiero asistencia al menos una vez y la fecha/hora del
     * primer check-in de cada usuario. {@code finales} es true solo con el evento FINALIZADO. Si MS01 no entrega
     * la población objetivo, esos dos datos quedan en null y el resto se entrega (RF-MS04-10, A2)
     *
     * @param eventoId evento; 404 si no existe
     * @param requester identidad de quien consulta, que se propaga a MS01
     * @return los resultados, finales o parciales
     */
    public Resultados resultados(UUID eventoId, RequesterIdentity requester) {
        Evento evento = eventos.findById(eventoId)
                .orElseThrow(() -> ApiException.noEncontrado(EVENTO_NO_ENCONTRADO));

        Optional<Set<UUID>> poblacion = poblacionObjetivo.usuariosActivosPorRoles(evento.getRolesConvocados(),
                corteDePoblacion(evento), requester);

        return lecturaConsistente.execute(status -> armar(eventoId, poblacion.orElse(null)));
    }

    /**
     * Corte de la población (RN-MS04-15): los usuarios creados antes del inicio del evento, o antes del momento de
     * la consulta si es anterior o si el evento aún no tiene fecha de inicio
     */
    Instant corteDePoblacion(Evento evento) {
        return evento.corteDePoblacion(clock.instant());
    }

    private Resultados armar(UUID eventoId, Set<UUID> poblacion) {
        // Se relee el evento dentro de la foto: su estado y fechas corresponden a los mismos check-ins
        Evento evento = eventos.findById(eventoId)
                .orElseThrow(() -> ApiException.noEncontrado(EVENTO_NO_ENCONTRADO));

        TotalesPorEstado totales = TotalesPorEstado.de(checkins.contarVigentesPorEstado(eventoId).stream()
                .collect(Collectors.toMap(CheckinRepository.ConteoPorEstado::getEstado,
                        CheckinRepository.ConteoPorEstado::getTotal)));

        List<TotalPorPunto> porPunto = checkins.contarSafePorPuntoEncuentro(eventoId).stream()
                .map(c -> new TotalPorPunto(c.getPuntoEncuentroId(), c.getTotal()))
                .toList();

        List<SolicitudResultado> solicitudes = checkins.buscarSolicitantesDeAsistencia(eventoId).stream()
                .map(s -> new SolicitudResultado(s.getUsuarioId(), resumen(s)))
                .toList();

        List<PrimerCheckin> primeros = checkins.buscarPrimerosCheckins(eventoId).stream()
                .map(p -> new PrimerCheckin(p.getUsuarioId(), p.getPrimerCheckinEn()))
                .toList();

        Long poblacionObjetivo = poblacion == null ? null : (long) poblacion.size();
        Long pendientes = poblacion == null ? null : contarPendientes(poblacion, primeros);

        return new Resultados(
                evento.getId(),
                evento.isSimulacro(),
                evento.getEstado(),
                evento.getFechaInicio(),
                evento.getFechaCierre(),
                evento.getEstado() == EstadoEvento.FINALIZADO,
                poblacionObjetivo,
                totales.total(),
                pendientes,
                totales,
                porPunto,
                solicitudes,
                primeros);
    }

    /**
     * Pendientes (RN-MS04-15): población objetivo menos quienes tienen estado vigente. Todo usuario con al menos un
     * check-in tiene estado vigente, así que los reportados son los usuarios de {@code primeros}. Quien reportó sin
     * estar en la población (p. ej. le cambiaron el rol) no resta pendientes
     */
    static long contarPendientes(Set<UUID> poblacion, List<PrimerCheckin> primeros) {
        Set<UUID> reportados = primeros.stream().map(PrimerCheckin::usuarioId).collect(Collectors.toSet());
        return poblacion.stream().filter(id -> !reportados.contains(id)).count();
    }

    private static SolicitudAsistenciaResumen resumen(CheckinRepository.SolicitudPorUsuario fila) {
        if (fila.getEstadoEnvio() == null) {
            return null;
        }
        return new SolicitudAsistenciaResumen(fila.getSolicitudId(), EstadoEnvio.valueOf(fila.getEstadoEnvio()));
    }
}
