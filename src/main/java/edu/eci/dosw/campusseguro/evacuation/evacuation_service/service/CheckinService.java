package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.PuntoEncuentro;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.PuntoEncuentroClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinRequest;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ResultadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Ubicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;

/**
 * RF-MS04-02 Registrar check-in y RF-MS04-03 Mantener y actualizar el estado vigente (Jira KAIR-573 y KAIR-574,
 * historias KAIR-590, KAIR-591 y KAIR-592).
 *
 * Los check-ins solo se agregan: cada reporte es un check-in nuevo y el estado vigente del usuario es el más
 * reciente (RN-MS04-07). El orden sigue 3_modelo-datos.md ("Operaciones"): la llamada a MS02 se hace antes de abrir
 * la transacción, y en la transacción se relee el evento con bloqueo compartido para que un FINALIZADO no se cruce
 */
@Service
public class CheckinService {

    public static final String EVENTO_NO_ENCONTRADO = "Evento no encontrado.";
    public static final String CHECKIN_NO_HABILITADO = "El check-in de este evento no está habilitado.";
    public static final String EVENTO_NO_APLICA = "Este evento no le aplica.";
    public static final String PUNTO_NO_ENCONTRADO = "Punto de encuentro no encontrado.";
    public static final String PUNTO_INACTIVO = "El punto de encuentro no está activo.";

    private final EventoRepository eventos;
    private final CheckinRepository checkins;
    private final PuntoEncuentroClient puntosEncuentro;
    private final TransactionTemplate transaccion;

    public CheckinService(EventoRepository eventos, CheckinRepository checkins, PuntoEncuentroClient puntosEncuentro,
            PlatformTransactionManager transactionManager) {
        this.eventos = eventos;
        this.checkins = checkins;
        this.puntosEncuentro = puntosEncuentro;
        this.transaccion = new TransactionTemplate(transactionManager);
    }

    /**
     * Registra el check-in del usuario en el evento (RF-MS04-02).
     *
     * Pasos: valida el cuerpo según el estado (RN-MS04-03, RG-05); verifica que el evento exista (404), que su
     * check-in esté habilitado (409) y que convoque alguno de los roles del usuario (422); si es SAFE, valida con
     * MS02 que el punto de encuentro exista (404) y esté ACTIVO (422), o 503 si MS02 no responde; y en una
     * transacción, relee el evento con bloqueo compartido, vuelve a verificarlo y agrega el check-in
     *
     * @param eventoId evento del check-in
     * @param request estado reportado y, según el estado, punto de encuentro o ubicación
     * @param requester identidad del solicitante; el usuario sale de aquí y nunca del cuerpo (RN-MS04-06)
     * @param correlationId correlation id de la petición, para las llamadas salientes y la auditoría
     * @return el check-in y si es el primero del usuario en el evento (201) o no (200)
     */
    public ResultadoCheckin registrar(UUID eventoId, CheckinRequest request, RequesterIdentity requester,
            String correlationId) {
        CheckinValidator.validar(request);
        // El evento se revisa antes de llamar a MS02, para no consultarlo si el check-in igual se va a rechazar
        verificarEvento(eventos.findById(eventoId), requester);
        if (request.estado() == EstadoCheckin.SAFE) {
            validarPuntoEncuentro(request.puntoEncuentroId(), requester, correlationId);
        }
        Registro registro = transaccion.execute(status -> guardar(eventoId, request, requester));
        return new ResultadoCheckin(aRespuesta(registro.checkin()), registro.creado());
    }

    /**
     * Transacción del check-in: relee el evento con bloqueo compartido (SELECT ... FOR SHARE), lo vuelve a verificar
     * por si cambió mientras se consultaba MS02, y agrega el check-in
     */
    private Registro guardar(UUID eventoId, CheckinRequest request, RequesterIdentity requester) {
        verificarEvento(eventos.buscarParaCheckin(eventoId), requester);
        Optional<Checkin> vigente = checkins.buscarVigente(eventoId, requester.userId());
        Checkin nuevo = checkins.save(nuevoCheckin(eventoId, request, requester));
        checkins.flush();
        return new Registro(nuevo, vigente.isEmpty());
    }

    /**
     * El evento debe existir (404), tener el check-in habilitado (409, RN-MS04-05) y convocar alguno de los roles
     * del usuario (422, RN-MS04-01)
     */
    private static Evento verificarEvento(Optional<Evento> encontrado, RequesterIdentity requester) {
        Evento evento = encontrado.orElseThrow(() -> ApiException.noEncontrado(EVENTO_NO_ENCONTRADO));
        if (!evento.isCheckinHabilitado()) {
            throw ApiException.conflicto(CHECKIN_NO_HABILITADO);
        }
        List<String> roles = requester.nombresDeRoles();
        if (evento.getRolesConvocados().stream().noneMatch(roles::contains)) {
            throw ApiException.reglaNegocio(EVENTO_NO_APLICA);
        }
        return evento;
    }

    /**
     * Estoy a salvo exige un punto de encuentro existente y ACTIVO en MS02 (RN-MS04-04)
     */
    private void validarPuntoEncuentro(UUID puntoEncuentroId, RequesterIdentity requester, String correlationId) {
        PuntoEncuentro punto = puntosEncuentro.buscar(puntoEncuentroId, requester, correlationId)
                .orElseThrow(() -> ApiException.noEncontrado(PUNTO_NO_ENCONTRADO));
        if (!punto.activo()) {
            throw ApiException.reglaNegocio(PUNTO_INACTIVO);
        }
    }

    private static Checkin nuevoCheckin(UUID eventoId, CheckinRequest request, RequesterIdentity requester) {
        Ubicacion ubicacion = request.ubicacion();
        boolean conUbicacion = request.estado() == EstadoCheckin.NEEDS_ASSISTANCE && ubicacion != null;
        return new Checkin(eventoId, requester.userId(), request.estado(),
                request.estado() == EstadoCheckin.SAFE ? request.puntoEncuentroId() : null,
                conUbicacion ? ubicacion.tipo() : null,
                conUbicacion ? ubicacion.id() : null);
    }

    /**
     * Respuesta del check-in. La solicitud de asistencia de un NEEDS_ASSISTANCE la agrega RF-MS04-04
     */
    private static CheckinResponse aRespuesta(Checkin checkin) {
        Ubicacion ubicacion = checkin.getUbicacionTipo() == null
                ? null
                : new Ubicacion(checkin.getUbicacionTipo(), checkin.getUbicacionId());
        return new CheckinResponse(checkin.getId(), checkin.getEventoId(), checkin.getEstado(),
                checkin.getPuntoEncuentroId(), ubicacion, null, checkin.getRegistradoEn());
    }

    /**
     * Resultado de la transacción del check-in
     *
     * @param checkin check-in guardado
     * @param creado true si es el primer check-in del usuario en el evento
     */
    private record Registro(Checkin checkin, boolean creado) {
    }
}
