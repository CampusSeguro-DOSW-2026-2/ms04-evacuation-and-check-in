package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinResumen;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Consolidado;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Pendiente;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PersonaAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Permiso;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.PermissionChecker;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.DashboardService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.Paginacion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    /**
     * Crea el controller con el service del dashboard
     *
     * @param service lógica de RF-MS04-06, RF-MS04-07 y RF-MS04-08
     */
    public DashboardController(DashboardService service) {
        this.service = service;
    }

    /**
     * Consolidado del evento por estado y punto de encuentro
     *
     * @param eventoId evento que se consolida
     * @param requester identidad tomada de las cabeceras X-User-Id y X-User-Role
     * @return los totales por estado, las personas a salvo por punto de encuentro y la fecha del cálculo
     * @throws ApiException 403 PROHIBIDO sin el permiso ms04:dashboard:read, 404 NO_ENCONTRADO si el evento no
     *         existe, 503 DEPENDENCIA_NO_DISPONIBLE si MS05 no entrega las zonas de un BRIGADISTA
     */
    @Operation(summary = "Consolidado del evento por estado y punto de encuentro (RF-MS04-06)",
            description = "Permiso ms04:dashboard:read. Se calcula con los estados vigentes y siempre incluye los "
                    + "cuatro estados. Funciona con el evento en cualquier estado. El BRIGADISTA solo recibe las "
                    + "personas SAFE y NEEDS_ASSISTANCE de sus zonas; EVACUATING y OFF_CAMPUS salen en 0.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Consolidado del evento."),
            @ApiResponse(responseCode = "401", description = "Faltan las cabeceras de identidad.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no tiene el permiso ms04:dashboard:read.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Evento inexistente.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503",
                    description = "MS05 no entrega las zonas del brigadista (DEPENDENCIA_NO_DISPONIBLE).",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{eventoId}/consolidado")
    public Consolidado consolidado(@PathVariable UUID eventoId, @Parameter(hidden = true) RequesterIdentity requester) {
        PermissionChecker.require(requester, Permiso.DASHBOARD_READ);
        return service.consolidado(eventoId, requester);
    }

    /**
     * Personas del evento con su estado vigente, filtradas por estado y punto de encuentro
     *
     * @param eventoId evento del que se listan las personas
     * @param estado filtro opcional por estado vigente
     * @param puntoEncuentroId filtro opcional por punto de encuentro, solo con SAFE o sin estado
     * @param page número de página, desde 0
     * @param size tamaño de página, de 1 a 100
     * @param requester identidad tomada de las cabeceras X-User-Id y X-User-Role
     * @return la página de estados vigentes con el nombre de cada persona
     * @throws ApiException 400 VALIDACION con paginación o filtros inválidos, 403 PROHIBIDO sin el permiso
     *         ms04:dashboard:read, 404 NO_ENCONTRADO si el evento no existe, 503 DEPENDENCIA_NO_DISPONIBLE si MS05
     *         no entrega las zonas de un BRIGADISTA
     */
    @Operation(summary = "Listar personas por estado y punto de encuentro (RF-MS04-06, F-02)",
            description = "Permiso ms04:dashboard:read. Estados vigentes del evento con el nombre de cada persona "
                    + "(nulo si MS01 no respondió), del reporte más reciente al más antiguo. punto_encuentro_id "
                    + "solo se combina con estado=SAFE o sin estado. El BRIGADISTA solo recibe las personas de "
                    + "sus zonas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de estados vigentes."),
            @ApiResponse(responseCode = "400",
                    description = "Estado, punto de encuentro o paginación inválidos, o punto de encuentro con un "
                            + "estado distinto de SAFE.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Faltan las cabeceras de identidad.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no tiene el permiso ms04:dashboard:read.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Evento inexistente.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503",
                    description = "MS05 no entrega las zonas del brigadista (DEPENDENCIA_NO_DISPONIBLE).",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{eventoId}/personas")
    public PageResponse<CheckinResumen> personas(@PathVariable UUID eventoId,
            @RequestParam(required = false) EstadoCheckin estado,
            @RequestParam(name = "punto_encuentro_id", required = false) UUID puntoEncuentroId,
            @RequestParam(defaultValue = Paginacion.PAGINA_POR_DEFECTO) int page,
            @RequestParam(defaultValue = Paginacion.TAMANO_POR_DEFECTO) int size,
            @Parameter(hidden = true) RequesterIdentity requester) {
        PermissionChecker.require(requester, Permiso.DASHBOARD_READ);
        Paginacion.validar(page, size);
        return service.personas(eventoId, estado, puntoEncuentroId, page, size, requester);
    }

    /**
     * Personas pendientes de reportar su estado, de la población objetivo del evento
     *
     * @param eventoId evento del que se listan los pendientes
     * @param page número de página, desde 0
     * @param size tamaño de página, de 1 a 100
     * @param requester identidad tomada de las cabeceras X-User-Id y X-User-Role
     * @return la página de pendientes, con el total de pendientes en {@code total_elementos}
     * @throws ApiException 400 VALIDACION con paginación inválida, 403 PROHIBIDO sin el permiso
     *         ms04:dashboard:read, 404 NO_ENCONTRADO si el evento no existe, 503 DEPENDENCIA_NO_DISPONIBLE si MS01
     *         no entrega la población objetivo
     */
    @Operation(summary = "Personas pendientes (RF-MS04-07, F-03)",
            description = "Permiso ms04:dashboard:read. Usuarios activos de MS01 con un rol convocado que no "
                    + "reportaron ninguno de los cuatro estados, ordenados por id de usuario, con su nombre (nulo "
                    + "si MS01 no lo entregó). total_elementos es el total de pendientes. El BRIGADISTA siempre "
                    + "recibe una lista vacía. Funciona con el evento en cualquier estado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de pendientes."),
            @ApiResponse(responseCode = "400", description = "Paginación inválida.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Faltan las cabeceras de identidad.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no tiene el permiso ms04:dashboard:read.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Evento inexistente.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503",
                    description = "MS01 no entrega la población objetivo (DEPENDENCIA_NO_DISPONIBLE).",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{eventoId}/pendientes")
    public PageResponse<Pendiente> pendientes(@PathVariable UUID eventoId,
            @RequestParam(defaultValue = Paginacion.PAGINA_POR_DEFECTO) int page,
            @RequestParam(defaultValue = Paginacion.TAMANO_POR_DEFECTO) int size,
            @Parameter(hidden = true) RequesterIdentity requester) {
        PermissionChecker.require(requester, Permiso.DASHBOARD_READ);
        Paginacion.validar(page, size);
        return service.pendientes(eventoId, page, size, requester);
    }

    /**
     * Personas que requieren asistencia; pendiente de implementar (RF-MS04-08)
     *
     * @param eventoId evento del que se listan las personas
     * @param page número de página, desde 0
     * @param size tamaño de página, de 1 a 100
     * @param requester identidad tomada de las cabeceras X-User-Id y X-User-Role
     * @return la página de personas que requieren asistencia
     * @throws ApiException 400 VALIDACION con paginación inválida, 403 PROHIBIDO sin el permiso
     *         ms04:dashboard:read, 501 NO_IMPLEMENTADO mientras no se implemente
     */
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
