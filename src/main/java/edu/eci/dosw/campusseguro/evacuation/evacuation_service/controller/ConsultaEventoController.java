package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EventoResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Permiso;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.PermissionChecker;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.ConsultaEventoService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.Paginacion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * RF-MS04-13 Consultar eventos. Solo lectura: ningún usuario crea, modifica ni elimina un evento (RN-MS04-26)
 */
@Tag(name = "Consulta de eventos")
@RestController
@RequestMapping("/api/v1/chi/eventos")
public class ConsultaEventoController {

    private final ConsultaEventoService service;

    public ConsultaEventoController(ConsultaEventoService service) {
        this.service = service;
    }

    @Operation(summary = "Listar eventos en cualquier estado (RF-MS04-13, F-07)",
            description = "Permiso ms04:dashboard:read. Eventos que MS04 conoce, tengan o no el check-in "
                    + "habilitado, ordenados por fecha de inicio del más reciente al más antiguo (los BORRADOR al "
                    + "final). El BRIGADISTA los ve todos, sin filtro de zona.")
    @GetMapping
    public PageResponse<EventoResponse> listar(@RequestParam(required = false) EstadoEvento estado,
            @RequestParam(defaultValue = Paginacion.PAGINA_POR_DEFECTO) int page,
            @RequestParam(defaultValue = Paginacion.TAMANO_POR_DEFECTO) int size,
            RequesterIdentity requester) {
        PermissionChecker.require(requester, Permiso.DASHBOARD_READ);
        Paginacion.validar(page, size);
        return service.listar(estado, page, size);
    }

    @Operation(summary = "Consultar un evento en cualquier estado (RF-MS04-13)",
            description = "Permiso ms04:dashboard:read. Devuelve el evento tal como lo guardó MS04, aunque su "
                    + "check-in esté deshabilitado.")
    @GetMapping("/{eventoId}")
    public EventoResponse obtener(@PathVariable UUID eventoId, RequesterIdentity requester) {
        PermissionChecker.require(requester, Permiso.DASHBOARD_READ);
        return service.obtener(eventoId);
    }
}
