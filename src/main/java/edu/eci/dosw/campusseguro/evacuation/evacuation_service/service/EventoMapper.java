package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.util.List;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Escenario;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EventoResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;

/**
 * Convierte un {@link Evento} en la respuesta del contrato protegido (snake_case). Lo usan las consultas que
 * devuelven eventos: los eventos activos (RF-MS04-01) y la consulta de eventos (RF-MS04-13)
 */
public final class EventoMapper {

    private EventoMapper() {
    }

    /**
     * Arma la respuesta de un evento tal como lo guarda MS04
     *
     * @param evento evento guardado
     * @return la respuesta con su escenario, sus roles convocados y si el check-in está habilitado
     */
    public static EventoResponse aRespuesta(Evento evento) {
        return new EventoResponse(
                evento.getId(),
                evento.getNombre(),
                evento.isSimulacro(),
                evento.getEstado(),
                evento.getFechaInicio(),
                evento.getFechaCierre(),
                new Escenario(evento.getEscenarioEdificios(), evento.getEscenarioZonas()),
                List.copyOf(evento.getRolesConvocados()),
                evento.isCheckinHabilitado());
    }
}
