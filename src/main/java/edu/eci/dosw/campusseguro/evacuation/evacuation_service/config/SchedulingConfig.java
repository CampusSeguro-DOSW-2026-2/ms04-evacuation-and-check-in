package edu.eci.dosw.campusseguro.evacuation.evacuation_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita las tareas periódicas de MS04: el reenvío de solicitudes de asistencia pendientes a MS05 (RN-MS04-12)
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
