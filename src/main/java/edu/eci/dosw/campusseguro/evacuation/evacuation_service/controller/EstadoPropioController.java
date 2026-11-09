package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Permiso;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.PermissionChecker;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.EstadoPropioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * RF-MS04-05 Consultar el propio estado vigente
 */
@Tag(name = "Check-in propio")
@RestController
@RequestMapping("/api/v1/chi/eventos")
public class EstadoPropioController {

    private final EstadoPropioService service;

    /**
     * Crea el controller con el service que consulta el estado vigente
     *
     * @param service lógica de RF-MS04-05
     */
    public EstadoPropioController(EstadoPropioService service) {
        this.service = service;
    }

    /**
     * Estado vigente del usuario autenticado en un evento
     *
     * @param eventoId evento del que se consulta el estado
     * @param requester identidad tomada de las cabeceras X-User-Id y X-User-Role
     * @return el check-in más reciente del usuario en el evento
     * @throws ApiException 403 PROHIBIDO sin el permiso ms04:checkin:read_own, 404 NO_ENCONTRADO si el evento no
     *         existe o el usuario no tiene check-in en él
     */
    @Operation(summary = "Consultar el propio estado vigente (RF-MS04-05)",
            description = "Permiso ms04:checkin:read_own. Identifica al usuario por X-User-Id y devuelve su "
                    + "check-in más reciente, no los anteriores. Funciona con el evento en cualquier estado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado vigente del usuario."),
            @ApiResponse(responseCode = "401", description = "Faltan las cabeceras de identidad.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no tiene el permiso ms04:checkin:read_own.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Evento inexistente o el usuario no tiene check-in en el evento.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{eventoId}/checkins/propio")
    public CheckinResponse propio(@PathVariable UUID eventoId, @Parameter(hidden = true) RequesterIdentity requester) {
        PermissionChecker.require(requester, Permiso.CHECKIN_READ_OWN);
        return service.consultar(eventoId, requester);
    }
}
