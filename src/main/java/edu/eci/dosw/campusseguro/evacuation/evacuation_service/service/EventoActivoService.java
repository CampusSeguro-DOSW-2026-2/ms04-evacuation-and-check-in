package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EventoResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;

/**
 * RF-MS04-01 Consultar eventos activos (Jira KAIR-572, historia KAIR-589).
 *
 * Solo lee la base de MS04: los eventos los guarda MS06 a través de RF-MS04-11 y esta consulta no llama a ningún
 * otro servicio (RN-MS04-01)
 */
@Service
public class EventoActivoService {

    private final EventoRepository eventos;

    public EventoActivoService(EventoRepository eventos) {
        this.eventos = eventos;
    }

    /**
     * Eventos EN_EJECUCION que le aplican al usuario: los que convocan alguno de sus roles, ordenados por fecha de
     * inicio. Un evento le aplica solo por los roles convocados; el escenario no restringe a quién le aplica
     * (RN-MS04-01, RN-MS04-24). Un usuario con varios roles ve los eventos de cualquiera de ellos, sin repetidos.
     * Devuelve una lista vacía si no hay ninguno (A1); si hay varios los devuelve todos (A2). No modifica datos
     *
     * @param requester identidad del solicitante (X-User-Id y X-User-Role)
     * @return los eventos activos que le aplican, que puede ser una lista vacía
     */
    @Transactional(readOnly = true)
    public List<EventoResponse> activos(RequesterIdentity requester) {
        return eventos.buscarActivosPorRoles(requester.nombresDeRoles()).stream()
                .map(EventoMapper::aRespuesta)
                .toList();
    }
}
