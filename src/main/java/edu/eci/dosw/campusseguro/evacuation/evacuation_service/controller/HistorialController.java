package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.RegistroHistorial;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Permiso;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.PermissionChecker;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.HistorialService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.Paginacion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * RF-MS04-09 Consultar el histórico de check-ins del evento
 */
@Tag(name = "Histórico y resultados")
@RestController
@RequestMapping("/api/v1/chi/eventos")
public class HistorialController {

    private final HistorialService service;

    public HistorialController(HistorialService service) {
        this.service = service;
    }

    @Operation(summary = "Histórico de check-ins del evento (RF-MS04-09, F-04)",
            description = "Permiso ms04:history:read. Todos los check-ins del evento (cada reporte de cada "
                    + "usuario), en orden cronológico. Disponible con el evento en cualquier estado.")
    @GetMapping("/{eventoId}/historial")
    public PageResponse<RegistroHistorial> historial(@PathVariable UUID eventoId,
            @RequestParam(name = "usuario_id", required = false) UUID usuarioId,
            @RequestParam(required = false) EstadoCheckin estado,
            @RequestParam(defaultValue = Paginacion.PAGINA_POR_DEFECTO) int page,
            @RequestParam(defaultValue = Paginacion.TAMANO_POR_DEFECTO) int size,
            RequesterIdentity requester) {
        PermissionChecker.require(requester, Permiso.HISTORY_READ);
        Paginacion.validar(page, size);
        return service.historial(eventoId, usuarioId, estado, page, size);
    }
}
