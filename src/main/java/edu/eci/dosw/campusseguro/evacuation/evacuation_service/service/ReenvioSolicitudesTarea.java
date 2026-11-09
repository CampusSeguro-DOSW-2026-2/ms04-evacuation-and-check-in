package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea periódica que reenvía a MS05 las solicitudes de asistencia PENDIENTE_ENVIO (RF-MS04-04, A2 y RN-MS04-12;
 * 3_modelo-datos.md, "Tareas de mantenimiento"). La frecuencia se configura con MS04_REENVIO_SOLICITUDES_MS
 * (30000 ms por defecto). Cada ejecución empieza cuando termina la anterior, así dos reenvíos nunca se cruzan
 */
@Component
public class ReenvioSolicitudesTarea {

    private static final Logger log = LoggerFactory.getLogger(ReenvioSolicitudesTarea.class);

    private final SolicitudAsistenciaService solicitudes;

    public ReenvioSolicitudesTarea(SolicitudAsistenciaService solicitudes) {
        this.solicitudes = solicitudes;
    }

    /**
     * Reenvía las pendientes. Nunca lanza: un error queda en el log y se reintenta en la siguiente ejecución
     */
    @Scheduled(fixedDelayString = "${solicitudes-asistencia.reenvio-ms:30000}",
            initialDelayString = "${solicitudes-asistencia.reenvio-ms:30000}")
    public void reenviar() {
        try {
            int enviadas = solicitudes.reenviarPendientes();
            if (enviadas > 0) {
                log.info("Reenvío de solicitudes de asistencia: {} enviadas a MS05", enviadas);
            }
        } catch (RuntimeException e) {
            log.error("Falló el reenvío de solicitudes de asistencia: {}", e.toString());
        }
    }
}
