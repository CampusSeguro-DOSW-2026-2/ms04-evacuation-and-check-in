package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinResumen;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Consolidado;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Pendiente;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PersonaAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Permiso;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.PermissionChecker;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.DashboardService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.Paginacion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * RF-MS04-06 Consolidar el estado del evento, RF-MS04-07 Identificar personas pendientes y RF-MS04-08 Consultar
 * personas que requieren asistencia. Todas exigen {@code ms04:dashboard:read}; el BRIGADISTA ve solo lo de sus zonas
 */
@Tag(name = "Consolidación")
@RestController
@RequestMapping("/api/v1/chi/eventos")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @Operation(summary = "Consolidado del evento por estado y punto de encuentro (RF-MS04-06)",
            description = "Permiso ms04:dashboard:read. Se calcula con los estados vigentes y siempre incluye los "
                    + "cuatro estados. Funciona con el evento en cualquier estado.")
    @GetMapping("/{eventoId}/consolidado")
    public Consolidado consolidado(@PathVariable UUID eventoId, RequesterIdentity requester) {
        PermissionChecker.require(requester, Permiso.DASHBOARD_READ);
        return service.consolidado(eventoId, requester);
    }

    @Operation(summary = "Listar personas por estado y punto de encuentro (RF-MS04-06, F-02)",
            description = "Permiso ms04:dashboard:read. Estados vigentes del evento con el nombre de cada persona. "
                    + "punto_encuentro_id solo se combina con estado=SAFE o sin estado.")
    @GetMapping("/{eventoId}/personas")
    public PageResponse<CheckinResumen> personas(@PathVariable UUID eventoId,
            @RequestParam(required = false) EstadoCheckin estado,
            @RequestParam(name = "punto_encuentro_id", required = false) UUID puntoEncuentroId,
            @RequestParam(defaultValue = Paginacion.PAGINA_POR_DEFECTO) int page,
            @RequestParam(defaultValue = Paginacion.TAMANO_POR_DEFECTO) int size,
            RequesterIdentity requester) {
        PermissionChecker.require(requester, Permiso.DASHBOARD_READ);
        Paginacion.validar(page, size);
        return service.personas(eventoId, estado, puntoEncuentroId, page, size, requester);
    }

    @Operation(summary = "Personas pendientes (RF-MS04-07, F-03)",
            description = "Permiso ms04:dashboard:read. Usuarios activos de MS01 con un rol convocado que no "
                    + "reportaron ninguno de los cuatro estados. El BRIGADISTA siempre recibe una lista vacía. "
                    + "Si MS01 no entrega la población objetivo responde 503.")
    @GetMapping("/{eventoId}/pendientes")
    public PageResponse<Pendiente> pendientes(@PathVariable UUID eventoId,
            @RequestParam(defaultValue = Paginacion.PAGINA_POR_DEFECTO) int page,
            @RequestParam(defaultValue = Paginacion.TAMANO_POR_DEFECTO) int size,
            RequesterIdentity requester) {
        PermissionChecker.require(requester, Permiso.DASHBOARD_READ);
        Paginacion.validar(page, size);
        return service.pendientes(eventoId, page, size, requester);
    }

    @Operation(summary = "Personas que requieren asistencia (RF-MS04-08, F-03)",
            description = "Permiso ms04:dashboard:read. Personas con estado vigente NEEDS_ASSISTANCE, del reporte "
                    + "más antiguo al más reciente, con su solicitud en MS05 y el estado de su envío.")
    @GetMapping("/{eventoId}/asistencia")
    public PageResponse<PersonaAsistencia> asistencia(@PathVariable UUID eventoId,
            @RequestParam(defaultValue = Paginacion.PAGINA_POR_DEFECTO) int page,
            @RequestParam(defaultValue = Paginacion.TAMANO_POR_DEFECTO) int size,
            RequesterIdentity requester) {
        PermissionChecker.require(requester, Permiso.DASHBOARD_READ);
        Paginacion.validar(page, size);
        return service.asistencia(eventoId, page, size, requester);
    }
}
