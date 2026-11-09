package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.RegistroHistorial;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Permiso;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.PermissionChecker;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.HistorialService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.Paginacion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * RF-MS04-09 Consultar el histórico de check-ins del evento. Exige {@code ms04:history:read} (COORDINADOR,
 * ORGANIZADOR y AUDITOR): el BRIGADISTA y la comunidad no lo consultan
 */
@Tag(name = "Histórico y resultados")
@RestController
@RequestMapping("/api/v1/chi/eventos")
public class HistorialController {

    private final HistorialService service;

    /**
     * Crea el controller del histórico
     *
     * @param service service del histórico de check-ins
     */
    public HistorialController(HistorialService service) {
        this.service = service;
    }

    /**
     * Histórico de check-ins del evento, del más antiguo al más reciente
     *
     * @param eventoId evento
     * @param usuarioId filtro opcional por usuario
     * @param estado filtro opcional por estado
     * @param page número de página, desde 0
     * @param size tamaño de página, de 1 a 100
     * @param requester identidad del usuario que consulta
     * @return la página del histórico
     * @throws ApiException 400 si el usuario, el estado o la paginación no son válidos, 403 si el rol no tiene
     *         ms04:history:read y 404 si el evento no existe
     */
    @Operation(summary = "Histórico de check-ins del evento (RF-MS04-09, F-04)",
            description = "Permiso ms04:history:read. Todos los check-ins del evento (cada reporte de cada "
                    + "usuario, no solo el vigente), en orden cronológico y con filtro opcional por usuario y por "
                    + "estado. Funciona con el evento en cualquier estado, y un evento sin check-ins devuelve la "
                    + "lista vacía.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página del histórico."),
            @ApiResponse(responseCode = "400",
                    description = "Usuario, estado o paginación inválidos.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Faltan las cabeceras de identidad.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no tiene el permiso ms04:history:read.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Evento inexistente.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{eventoId}/historial")
    public PageResponse<RegistroHistorial> historial(@PathVariable UUID eventoId,
            @RequestParam(name = "usuario_id", required = false) UUID usuarioId,
            @RequestParam(required = false) EstadoCheckin estado,
            @RequestParam(defaultValue = Paginacion.PAGINA_POR_DEFECTO) int page,
            @RequestParam(defaultValue = Paginacion.TAMANO_POR_DEFECTO) int size,
            @Parameter(hidden = true) RequesterIdentity requester) {
        PermissionChecker.require(requester, Permiso.HISTORY_READ);
        Paginacion.validar(page, size);
        return service.historial(eventoId, usuarioId, estado, page, size);
    }
}
