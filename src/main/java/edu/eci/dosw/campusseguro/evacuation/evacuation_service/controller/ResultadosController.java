package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Resultados;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Permiso;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.PermissionChecker;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.ResultadosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * RF-MS04-10 Entregar resultados del evento
 */
@Tag(name = "Histórico y resultados")
@RestController
@RequestMapping("/api/v1/chi/eventos")
public class ResultadosController {

    private final ResultadosService service;

    public ResultadosController(ResultadosService service) {
        this.service = service;
    }

    @Operation(summary = "Resultados del evento (RF-MS04-10)",
            description = "Permiso ms04:history:read. Datos de check-in del evento; finales es false mientras el "
                    + "evento no esté FINALIZADO. MS04 no calcula indicadores. La política de acceso para MS06 y "
                    + "MS08 está pendiente (decisión abierta n.º 6).")
    @GetMapping("/{eventoId}/resultados")
    public Resultados resultados(@PathVariable UUID eventoId, RequesterIdentity requester) {
        PermissionChecker.require(requester, Permiso.HISTORY_READ);
        return service.resultados(eventoId);
    }
}
