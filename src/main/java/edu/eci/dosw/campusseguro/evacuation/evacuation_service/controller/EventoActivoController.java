package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EventoResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Permiso;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.PermissionChecker;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.EventoActivoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * RF-MS04-01 Consultar eventos activos
 */
@Tag(name = "Evento activo")
@RestController
@RequestMapping("/api/v1/chi/eventos")
public class EventoActivoController {

    private final EventoActivoService service;

    public EventoActivoController(EventoActivoService service) {
        this.service = service;
    }

    @Operation(summary = "Consultar eventos activos que le aplican al usuario (RF-MS04-01)",
            description = "Permiso ms04:checkin:read_own. Eventos EN_EJECUCION cuyos roles convocados incluyen el "
                    + "rol del usuario, ordenados por fecha de inicio. Lista vacía si no hay ninguno.")
    @GetMapping("/activos")
    public List<EventoResponse> activos(RequesterIdentity requester) {
        PermissionChecker.require(requester, Permiso.CHECKIN_READ_OWN);
        return service.activos(requester);
    }
}
