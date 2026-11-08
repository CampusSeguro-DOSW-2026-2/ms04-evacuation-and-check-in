package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Resultados;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;

/**
 * RF-MS04-10 Entregar resultados del evento (Jira KAIR-583, historias KAIR-605 y KAIR-606). MS04 entrega datos
 * y no calcula indicadores (RN-MS04-18).
 *
 * Pendiente de implementar: responde 501 hasta que se complete la lógica
 */
@Service
public class ResultadosService {

    /**
     * Resultados de check-in del evento: población objetivo, total que reportó y pendientes, totales por estado y
     * por punto de encuentro, personas que reportaron Requiero asistencia al menos una vez y la fecha/hora del
     * primer check-in de cada usuario. {@code finales} es true solo con el evento FINALIZADO. Si MS01 no entrega
     * la población objetivo, esos dos datos quedan en null y el resto se entrega (RF-MS04-10, A2)
     *
     * @param eventoId evento; 404 si no existe
     * @return los resultados, finales o parciales
     */
    public Resultados resultados(UUID eventoId) {
        throw ApiException.noImplementado("RF-MS04-10 resultados del evento");
    }
}
