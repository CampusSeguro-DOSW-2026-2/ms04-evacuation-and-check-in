package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.UsuarioNombresClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.DetalleCheckinMs06;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.DetalleCheckinsMs06;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;

/**
 * RF-MS04-12 Entregar a MS06 el detalle de check-ins del evento (Jira KAIR-581, historia KAIR-603). Es una ruta
 * interna: la autoriza la cadena de seguridad por X-Service-Name (RN-MS04-23), no la Matriz Rol-Permiso.
 *
 * Pasos:
 * 1. El evento debe existir (404); su estado no importa (RN-MS04-22)
 * 2. Lee una página de estados vigentes (el check-in más reciente de cada persona), del más antiguo al más
 *    reciente, filtrada por estado si se pidió
 * 3. Pide a MS01 los nombres de esa página; si MS01 no responde, {@code nombre} queda null y el fallo ya quedó
 *    en el log del cliente (RN-MS04-25, A6)
 * 4. Responde en el formato acordado con MS06: {@code page}, {@code size}, {@code total} e {@code items}
 *    (RNF-MS04-16)
 */
@Service
public class DetalleCheckinsService {

    static final String EVENTO_NO_ENCONTRADO = "Evento no encontrado.";

    private final EventoRepository eventos;
    private final CheckinRepository checkins;
    private final UsuarioNombresClient nombres;

    /**
     * @param eventos repositorio de eventos
     * @param checkins repositorio de check-ins
     * @param nombres cliente de los nombres de MS01
     */
    public DetalleCheckinsService(EventoRepository eventos, CheckinRepository checkins,
            UsuarioNombresClient nombres) {
        this.eventos = eventos;
        this.checkins = checkins;
        this.nombres = nombres;
    }

    /**
     * Estado vigente de cada persona en el evento (su check-in más reciente), del más antiguo al más reciente, con
     * filtro opcional por estado. {@code zonaId} es el punto de encuentro, solo con SAFE. Funciona con el evento en
     * cualquier estado
     *
     * @param eventoId evento; 404 si no existe
     * @param estado filtro opcional por estado vigente
     * @param page número de página, desde 0 (ya validado por el controller)
     * @param size tamaño de página, de 1 a 100 (ya validado por el controller)
     * @return la página en el formato acordado con MS06
     */
    public DetalleCheckinsMs06 detalle(UUID eventoId, EstadoCheckin estado, int page, int size) {
        if (!eventos.existsById(eventoId)) {
            throw ApiException.noEncontrado(EVENTO_NO_ENCONTRADO);
        }
        Page<Checkin> pagina = checkins.buscarVigentesParaMs06(eventoId, estado == null ? null : estado.name(),
                PageRequest.of(page, size));

        Map<UUID, String> nombresPorUsuario = pagina.isEmpty()
                ? Map.of()
                : nombres.nombresDe(pagina.getContent().stream().map(Checkin::getUsuarioId).toList());

        List<DetalleCheckinMs06> items = pagina.getContent().stream()
                .map(checkin -> new DetalleCheckinMs06(
                        checkin.getUsuarioId(),
                        nombresPorUsuario.get(checkin.getUsuarioId()),
                        checkin.getEstado(),
                        checkin.getEstado() == EstadoCheckin.SAFE ? checkin.getPuntoEncuentroId() : null,
                        checkin.getRegistradoEn()))
                .toList();
        return new DetalleCheckinsMs06(page, size, pagina.getTotalElements(), items);
    }
}
