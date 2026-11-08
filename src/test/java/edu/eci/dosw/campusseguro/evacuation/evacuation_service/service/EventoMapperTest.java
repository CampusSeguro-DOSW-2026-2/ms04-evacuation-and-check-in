package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EventoResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;

class EventoMapperTest {

    private static final UUID ID = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final Instant INICIO = Instant.parse("2026-11-20T15:00:00Z");

    private static Evento evento(EstadoEvento estado) {
        Evento evento = new Evento(ID);
        evento.setNombre("Simulacro de evacuación bloque A");
        evento.setSimulacro(true);
        evento.setEstado(estado);
        evento.setFechaInicio(estado == EstadoEvento.BORRADOR ? null : INICIO);
        evento.setRolesConvocados(List.of("ESTUDIANTE", "PROFESOR"));
        evento.setEscenarioEdificios(List.of("Bloque A"));
        evento.setEscenarioZonas(List.of("Zona norte"));
        return evento;
    }

    @Test
    void copiaTodosLosCamposDelEvento() {
        EventoResponse respuesta = EventoMapper.aRespuesta(evento(EstadoEvento.EN_EJECUCION));

        assertEquals(ID, respuesta.eventoId());
        assertEquals("Simulacro de evacuación bloque A", respuesta.nombre());
        assertTrue(respuesta.simulacro());
        assertEquals(EstadoEvento.EN_EJECUCION, respuesta.estado());
        assertEquals(INICIO, respuesta.fechaInicio());
        assertNull(respuesta.fechaCierre());
        assertEquals(List.of("Bloque A"), respuesta.escenario().edificios());
        assertEquals(List.of("Zona norte"), respuesta.escenario().zonas());
        assertEquals(List.of("ESTUDIANTE", "PROFESOR"), respuesta.rolesConvocados());
    }

    @Test
    void elCheckinEstaHabilitadoSoloConElEventoEnEjecucion() {
        assertTrue(EventoMapper.aRespuesta(evento(EstadoEvento.EN_EJECUCION)).checkinHabilitado());
        for (EstadoEvento estado : List.of(EstadoEvento.BORRADOR, EstadoEvento.PROGRAMADO,
                EstadoEvento.FINALIZADO)) {
            assertFalse(EventoMapper.aRespuesta(evento(estado)).checkinHabilitado(), estado.name());
        }
    }

    @Test
    void unBorradorNoTieneFechaDeInicio() {
        assertNull(EventoMapper.aRespuesta(evento(EstadoEvento.BORRADOR)).fechaInicio());
    }

    @Test
    void unEventoFinalizadoConservaSuFechaDeCierre() {
        Evento finalizado = evento(EstadoEvento.FINALIZADO);
        finalizado.setFechaCierre(Instant.parse("2026-11-20T15:45:00Z"));

        assertEquals(Instant.parse("2026-11-20T15:45:00Z"), EventoMapper.aRespuesta(finalizado).fechaCierre());
    }

    @Test
    void elEscenarioSinDatosSaleConListasVacias() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        evento.setEscenarioEdificios(null);
        evento.setEscenarioZonas(null);

        EventoResponse respuesta = EventoMapper.aRespuesta(evento);

        assertTrue(respuesta.escenario().edificios().isEmpty());
        assertTrue(respuesta.escenario().zonas().isEmpty());
    }

    @Test
    void laRespuestaNoComparteLasListasConLaEntidad() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);

        EventoResponse respuesta = EventoMapper.aRespuesta(evento);
        evento.getRolesConvocados().add("COORDINADOR");

        assertEquals(List.of("ESTUDIANTE", "PROFESOR"), respuesta.rolesConvocados());
        assertThrows(UnsupportedOperationException.class, () -> respuesta.rolesConvocados().add("X"));
    }
}
