package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinRequest;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ResultadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Permiso;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.PermissionChecker;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.CheckinService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * RF-MS04-02 Registrar check-in y RF-MS04-03 Mantener y actualizar el estado vigente
 */
@Tag(name = "Check-in propio")
@RestController
@RequestMapping("/api/v1/chi/eventos")
public class CheckinController {

    private final CheckinService service;

    public CheckinController(CheckinService service) {
        this.service = service;
    }

    @Operation(summary = "Registrar o actualizar el propio check-in (RF-MS04-02, RF-MS04-03, RF-MS04-04)",
            description = "Permisos ms04:checkin:write y, con NEEDS_ASSISTANCE, también ms04:assistance:request. "
                    + "Solo con el evento EN_EJECUCION y si el rol del usuario está entre los roles convocados. "
                    + "201 si es el primer check-in del usuario en el evento; 200 si agrega uno nuevo o repite el "
                    + "vigente. Un campo que no corresponde al estado o un id de usuario en el cuerpo se rechaza "
                    + "con 400.")
    @PostMapping("/{eventoId}/checkins")
    public ResponseEntity<CheckinResponse> registrar(@PathVariable UUID eventoId,
            @RequestBody CheckinRequest request, RequesterIdentity requester,
            HttpServletRequest http) {
        PermissionChecker.require(requester, Permiso.CHECKIN_WRITE);
        if (request.estado() == EstadoCheckin.NEEDS_ASSISTANCE) {
            PermissionChecker.require(requester, Permiso.ASSISTANCE_REQUEST);
        }
        ResultadoCheckin resultado = service.registrar(eventoId, request, requester, CorrelationIds.current(http));
        return ResponseEntity.status(resultado.creado() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(resultado.checkin());
    }
}
