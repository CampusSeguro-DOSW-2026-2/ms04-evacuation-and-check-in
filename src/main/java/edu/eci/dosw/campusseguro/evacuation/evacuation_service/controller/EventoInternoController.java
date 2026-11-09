package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.DetalleCheckinsMs06;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EstadoEventoRequest;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EventoInterno;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ResultadoEstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.DetalleCheckinsService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.EstadoEventoService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.Paginacion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Rutas internas de MS06 (RN-MS04-23): RF-MS04-11 Recibir el estado del evento y RF-MS04-12 Entregar a MS06 el
 * detalle de check-ins. No se autorizan por la Matriz Rol-Permiso sino por el servicio que llama: la cadena de
 * seguridad exige X-Service-Name y solo deja pasar a los servicios autorizados (por defecto ms06), así que aquí
 * no se vuelve a comprobar
 */
@Tag(name = "Estado del evento (interno MS06)")
@RestController
@RequestMapping("/api/v1/chi/eventos")
public class EventoInternoController {

    private final EstadoEventoService estadoEventoService;
    private final DetalleCheckinsService detalleCheckinsService;

    public EventoInternoController(EstadoEventoService estadoEventoService,
            DetalleCheckinsService detalleCheckinsService) {
        this.estadoEventoService = estadoEventoService;
        this.detalleCheckinsService = detalleCheckinsService;
    }

    @Operation(summary = "Recibir el estado del evento y habilitar o deshabilitar el check-in (RF-MS04-11, F-05)",
            description = "Ruta interna de MS06. Crea el evento si no existe (201) o lo actualiza (200). "
                    + "EN_EJECUCION habilita el check-in y FINALIZADO lo deshabilita. Repetir EN_EJECUCION o "
                    + "FINALIZADO es idempotente. Las transiciones permitidas están en RN-MS04-21; cualquier otra, "
                    + "o cambiar simulacro, responde 409.")
    @PutMapping("/{eventoId}/estado")
    public ResponseEntity<EventoInterno> recibirEstado(@PathVariable UUID eventoId,
            @RequestBody EstadoEventoRequest request,
            @RequestHeader(IdentityHeaders.SERVICE_NAME) String servicio, HttpServletRequest http) {
        ResultadoEstadoEvento resultado = estadoEventoService.recibir(eventoId, request, servicio,
                CorrelationIds.current(http));
        return ResponseEntity.status(resultado.creado() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(resultado.evento());
    }

    @Operation(summary = "Entregar a MS06 el detalle de check-ins del evento (RF-MS04-12, F-06)",
            description = "Ruta interna de MS06. Un elemento por persona con su estado vigente, del más antiguo al "
                    + "más reciente. zonaId es el punto de encuentro y solo viene con SAFE. Usa el formato "
                    + "acordado con MS06 (page, size, total, items). Funciona con el evento en cualquier estado.")
    @GetMapping("/{eventoId}/checkins")
    public DetalleCheckinsMs06 detalleCheckins(@PathVariable UUID eventoId,
            @RequestParam(required = false) EstadoCheckin estado,
            @RequestParam(defaultValue = Paginacion.PAGINA_POR_DEFECTO) int page,
            @RequestParam(defaultValue = Paginacion.TAMANO_POR_DEFECTO) int size) {
        Paginacion.validar(page, size);
        return detalleCheckinsService.detalle(eventoId, estado, page, size);
    }
}
