package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinRequest;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ResultadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;

/**
 * RF-MS04-02 Registrar check-in y RF-MS04-03 Mantener y actualizar el estado vigente (Jira KAIR-573 y KAIR-574,
 * historias KAIR-590, KAIR-591 y KAIR-592).
 *
 * Pendiente de implementar: las operaciones responden 501 hasta que se complete la lógica
 */
@Service
public class CheckinService {

    /**
     * Registra el primer check-in del usuario en el evento o, si ya tiene uno, lo actualiza agregando un
     * check-in nuevo: los check-ins solo se agregan (RN-MS04-07).
     *
     * Orden previsto (3_modelo-datos.md, "Operaciones"): validar el cuerpo según el estado (RG-05); si es SAFE,
     * consultar a MS02 el punto de encuentro antes de abrir la transacción; en la transacción, tomar
     * {@code CheckinRepository#bloquearUsuarioEnEvento}, leer el evento con
     * {@code EventoRepository#buscarParaCheckin} (debe estar EN_EJECUCION y convocar el rol del usuario) y comparar
     * con {@code CheckinRepository#buscarVigente}; si cambia el estado o el punto, insertar el check-in. Después
     * de confirmar, la solicitud de asistencia (RF-MS04-04) y la auditoría
     *
     * @param eventoId evento del check-in
     * @param request estado reportado y, según el estado, punto de encuentro o ubicación
     * @param requester identidad del solicitante; el usuario sale de aquí y nunca del cuerpo (RN-MS04-06)
     * @param correlationId correlation id de la petición, para la auditoría y las llamadas salientes
     * @return el check-in vigente y si es el primero del usuario en el evento
     */
    public ResultadoCheckin registrar(UUID eventoId, CheckinRequest request, RequesterIdentity requester,
            String correlationId) {
        throw ApiException.noImplementado("RF-MS04-02 registrar y RF-MS04-03 actualizar el check-in");
    }
}
