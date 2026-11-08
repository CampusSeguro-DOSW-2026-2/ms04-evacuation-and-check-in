package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EstadoEventoRequest;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ResultadoEstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;

/**
 * RF-MS04-11 Recibir el estado del evento y habilitar o deshabilitar el check-in (Jira KAIR-571, historia
 * KAIR-588). Es la única operación que crea o modifica eventos; MS04 nunca inicia ni termina un evento por reloj.
 *
 * Pendiente de implementar: responde 501 hasta que se complete la lógica
 */
@Service
public class EstadoEventoService {

    /**
     * Crea o actualiza el evento con el estado que informa MS06. Estado efectivo: con {@code simulacro = false},
     * cualquier estado distinto de FINALIZADO deja el evento EN_EJECUCION (RN-MS04-20). En una transacción, lee el
     * evento con {@code EventoRepository#buscarParaActualizar} y valida la transición de RN-MS04-21; cualquier otra
     * combinación, o un {@code simulacro} distinto del guardado, responde 409. Repetir EN_EJECUCION o FINALIZADO
     * no escribe nada. Con FINALIZADO guarda la fecha de cierre. Audita CAMBIO_ESTADO_EVENTO con el servicio de
     * origen y los estados anterior y nuevo en el detalle
     *
     * @param eventoId id del evento que asigna MS06 (el de la ruta)
     * @param request estado del evento; su {@code eventoId} debe ser igual al de la ruta
     * @param servicio servicio que llama (X-Service-Name), para la auditoría
     * @param correlationId correlation id de la petición
     * @return el evento almacenado y si era nuevo (201) o ya existía (200)
     */
    public ResultadoEstadoEvento recibir(UUID eventoId, EstadoEventoRequest request, String servicio,
            String correlationId) {
        throw ApiException.noImplementado("RF-MS04-11 recibir el estado del evento");
    }
}
