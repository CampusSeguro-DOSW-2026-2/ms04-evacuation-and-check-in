package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.RegistroHistorial;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;

/**
 * RF-MS04-09 Conservar eventos finalizados y consultar su histórico (Jira KAIR-580, historias KAIR-601 y
 * KAIR-602). El cierre del evento lo recibe {@link EstadoEventoService}; este service solo consulta.
 *
 * Pendiente de implementar: responde 501 hasta que se complete la lógica
 */
@Service
public class HistorialService {

    /**
     * Todos los check-ins del evento (cada reporte de cada usuario), en orden cronológico, con filtros opcionales
     * por usuario y estado. El histórico es la propia tabla {@code checkins}: no hay una tabla aparte
     * (RN-MS04-08). Funciona con el evento en cualquier estado
     *
     * @param eventoId evento; 404 si no existe
     * @param usuarioId filtro opcional por usuario
     * @param estado filtro opcional por estado
     * @param page número de página, desde 0
     * @param size tamaño de página, de 1 a 100
     * @return la página del histórico
     */
    public PageResponse<RegistroHistorial> historial(UUID eventoId, UUID usuarioId, EstadoCheckin estado, int page,
            int size) {
        throw ApiException.noImplementado("RF-MS04-09 histórico de check-ins del evento");
    }
}
