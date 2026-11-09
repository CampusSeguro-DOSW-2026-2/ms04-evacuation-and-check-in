package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.RegistroHistorial;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Ubicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;

/**
 * RF-MS04-09 Conservar eventos finalizados y consultar su histórico (Jira KAIR-580, historias KAIR-601 y
 * KAIR-602). El cierre del evento lo recibe {@link EstadoEventoService}; este service solo consulta.
 *
 * Pasos:
 * 1. El evento debe existir (404); su estado no importa (RN-MS04-22)
 * 2. Lee una página de check-ins del evento, todos y no solo el vigente de cada persona, del más antiguo al más
 *    reciente, filtrada por usuario y por estado si se pidió
 * 3. Responde con la página en el formato de los Estándares; un evento sin check-ins devuelve la lista vacía
 *
 * No modifica ningún dato: los check-ins de un evento FINALIZADO se conservan tal como estaban al cierre
 * (RN-MS04-17)
 */
@Service
public class HistorialService {

    static final String EVENTO_NO_ENCONTRADO = "Evento no encontrado.";

    private final EventoRepository eventos;
    private final CheckinRepository checkins;

    /**
     * Crea el service del histórico de check-ins
     *
     * @param eventos repositorio de eventos
     * @param checkins repositorio de check-ins
     */
    public HistorialService(EventoRepository eventos, CheckinRepository checkins) {
        this.eventos = eventos;
        this.checkins = checkins;
    }

    /**
     * Todos los check-ins del evento (cada reporte de cada usuario), en orden cronológico, con filtros opcionales
     * por usuario y estado. El histórico es la propia tabla {@code checkins}: no hay una tabla aparte
     * (RN-MS04-08). Funciona con el evento en cualquier estado
     *
     * @param eventoId evento
     * @param usuarioId filtro opcional por usuario
     * @param estado filtro opcional por estado
     * @param page número de página, desde 0 (ya validado por el controller)
     * @param size tamaño de página, de 1 a 100 (ya validado por el controller)
     * @return la página del histórico
     * @throws ApiException 404 NO_ENCONTRADO si el evento no existe
     */
    public PageResponse<RegistroHistorial> historial(UUID eventoId, UUID usuarioId, EstadoCheckin estado, int page,
            int size) {
        if (!eventos.existsById(eventoId)) {
            throw ApiException.noEncontrado(EVENTO_NO_ENCONTRADO);
        }
        Page<Checkin> pagina = checkins.buscarHistorial(eventoId, usuarioId, estado, PageRequest.of(page, size));
        List<RegistroHistorial> registros = new ArrayList<>();
        for (Checkin checkin : pagina.getContent()) {
            Ubicacion ubicacion = null;
            if (checkin.getUbicacionTipo() != null) {
                ubicacion = new Ubicacion(checkin.getUbicacionTipo(), checkin.getUbicacionId());
            }
            registros.add(new RegistroHistorial(checkin.getId(), checkin.getUsuarioId(), checkin.getEstado(),
                    checkin.getPuntoEncuentroId(), ubicacion, checkin.getRegistradoEn()));
        }
        return PageResponse.of(registros, page, size, pagina.getTotalElements());
    }
}
