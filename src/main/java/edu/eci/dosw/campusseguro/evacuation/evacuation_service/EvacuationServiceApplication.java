package edu.eci.dosw.campusseguro.evacuation.evacuation_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MS04 - Evacuación y check-in (CampusSeguro)
 *
 * Recibe de MS06 el estado de cada evento, habilita el check-in solo con el evento en ejecución y consolida el
 * estado de las personas por punto de encuentro
 */
@SpringBootApplication
public class EvacuationServiceApplication {

    /**
     * Arranca el microservicio
     *
     * @param args argumentos de la línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(EvacuationServiceApplication.class, args);
    }
}
