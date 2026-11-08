package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EventoResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Rol;

class EventoActivoServiceTest {

    private static final UUID USUARIO = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");

    private EventoRepository eventos;
    private EventoActivoService service;

    @BeforeEach
    void preparar() {
        eventos = mock(EventoRepository.class);
        service = new EventoActivoService(eventos);
    }

    private static Evento activo(String nombre) {
        Evento evento = new Evento(UUID.randomUUID());
        evento.setNombre(nombre);
        evento.setSimulacro(true);
        evento.setEstado(EstadoEvento.EN_EJECUCION);
        evento.setFechaInicio(Instant.parse("2026-11-20T15:00:00Z"));
        evento.setRolesConvocados(List.of("ESTUDIANTE"));
        return evento;
    }

    @Test
    void buscaPorLosRolesDelUsuarioEnOrdenAlfabetico() {
        RequesterIdentity usuario = new RequesterIdentity(USUARIO, Set.of(Rol.ESTUDIANTE, Rol.BRIGADISTA));

        service.activos(usuario);

        verify(eventos).buscarActivosPorRoles(List.of("BRIGADISTA", "ESTUDIANTE"));
        verifyNoMoreInteractions(eventos);
    }

    @Test
    void convierteCadaEventoEnSuRespuestaConservandoElOrden() {
        Evento primero = activo("Primero");
        Evento segundo = activo("Segundo");
        when(eventos.buscarActivosPorRoles(List.of("ESTUDIANTE"))).thenReturn(List.of(primero, segundo));

        List<EventoResponse> respuesta = service.activos(new RequesterIdentity(USUARIO, Set.of(Rol.ESTUDIANTE)));

        assertEquals(List.of(primero.getId(), segundo.getId()),
                respuesta.stream().map(EventoResponse::eventoId).toList());
        assertEquals(List.of("Primero", "Segundo"), respuesta.stream().map(EventoResponse::nombre).toList());
        assertTrue(respuesta.stream().allMatch(EventoResponse::checkinHabilitado));
    }

    @Test
    void sinEventosActivosDevuelveUnaListaVaciaYNoFalla() {
        when(eventos.buscarActivosPorRoles(List.of("ESTUDIANTE"))).thenReturn(List.of());

        assertTrue(service.activos(new RequesterIdentity(USUARIO, Set.of(Rol.ESTUDIANTE))).isEmpty());
    }
}
