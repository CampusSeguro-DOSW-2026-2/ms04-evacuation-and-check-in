package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Permiso;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.PermissionChecker;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.EstadoPropioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * RF-MS04-05 Consultar el propio estado vigente
 */
@Tag(name = "Check-in propio")
@RestController
@RequestMapping("/api/v1/chi/eventos")
public class EstadoPropioController {

    private final EstadoPropioService service;

    public EstadoPropioController(EstadoPropioService service) {
        this.service = service;
    }

    @Operation(summary = "Consultar el propio estado vigente (RF-MS04-05)",
            description = "Permiso ms04:checkin:read_own. Identifica al usuario por X-User-Id y devuelve su "
                    + "check-in más reciente, no los anteriores. Funciona con el evento en cualquier estado.")
    @GetMapping("/{eventoId}/checkins/propio")
    public CheckinResponse propio(@PathVariable UUID eventoId, RequesterIdentity requester) {
        PermissionChecker.require(requester, Permiso.CHECKIN_READ_OWN);
        return service.consultar(eventoId, requester);
    }
}
