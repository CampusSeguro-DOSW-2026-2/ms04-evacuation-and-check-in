package edu.eci.dosw.campusseguro.evacuation.evacuation_service;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.PostgresTestcontainerConfig;

/**
 * RF-MS04-01 Consultar eventos activos, de punta a punta: la petición HTTP con las cabeceras del Gateway, el
 * permiso, la consulta a PostgreSQL real y la respuesta del contrato. Cada prueba se deshace al terminar
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainerConfig.class)
@Transactional
class EventoActivoIntegracionTest {

    private static final String USUARIO = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";
    private static final String RUTA = "/api/v1/chi/eventos/activos";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventoRepository eventos;

    /** Guarda un evento con fechas coherentes con su estado (las exige la base de datos) */
    private Evento evento(String nombre, EstadoEvento estado, String inicio, List<String> roles) {
        Evento evento = new Evento(UUID.randomUUID());
        evento.setNombre(nombre);
        evento.setSimulacro(true);
        evento.setEstado(estado);
        evento.setFechaInicio(estado == EstadoEvento.BORRADOR ? null : Instant.parse(inicio));
        evento.setFechaCierre(estado == EstadoEvento.FINALIZADO ? Instant.parse("2026-11-20T18:00:00Z") : null);
        evento.setRolesConvocados(roles);
        evento.setEscenarioEdificios(List.of("Bloque A"));
        evento.setEscenarioZonas(List.of("Zona norte"));
        return eventos.saveAndFlush(evento);
    }

    private MockHttpServletRequestBuilder como(String roles) {
        return get(RUTA).header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, roles);
    }

    @Test
    void sinEventosEnLaBaseDevuelve200ConListaVacia() throws Exception {
        mockMvc.perform(como("ESTUDIANTE"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void soloDevuelveLosEventosEnEjecucion() throws Exception {
        Evento enEjecucion = evento("En ejecución", EstadoEvento.EN_EJECUCION, "2026-11-20T15:00:00Z",
                List.of("ESTUDIANTE"));
        evento("Programado", EstadoEvento.PROGRAMADO, "2026-11-25T15:00:00Z", List.of("ESTUDIANTE"));
        evento("Borrador", EstadoEvento.BORRADOR, null, List.of("ESTUDIANTE"));
        evento("Finalizado", EstadoEvento.FINALIZADO, "2026-11-10T15:00:00Z", List.of("ESTUDIANTE"));

        mockMvc.perform(como("ESTUDIANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].evento_id").value(enEjecucion.getId().toString()));
    }

    @Test
    void soloDevuelveLosEventosQueConvocanElRolDelUsuario() throws Exception {
        Evento paraEstudiantes = evento("Estudiantes y profesores", EstadoEvento.EN_EJECUCION,
                "2026-11-20T15:00:00Z", List.of("ESTUDIANTE", "PROFESOR"));
        Evento paraBrigadistas = evento("Brigadistas", EstadoEvento.EN_EJECUCION, "2026-11-20T16:00:00Z",
                List.of("BRIGADISTA"));

        mockMvc.perform(como("ESTUDIANTE"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].evento_id").value(paraEstudiantes.getId().toString()));
        mockMvc.perform(como("BRIGADISTA"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].evento_id").value(paraBrigadistas.getId().toString()));
        mockMvc.perform(como("ADMINISTRATIVO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void unUsuarioConVariosRolesVeLosEventosDeCualquieraSinRepetidosYPorFechaDeInicio() throws Exception {
        Evento tarde = evento("Tarde", EstadoEvento.EN_EJECUCION, "2026-11-20T17:00:00Z", List.of("BRIGADISTA"));
        Evento ambos = evento("Ambos roles", EstadoEvento.EN_EJECUCION, "2026-11-20T09:00:00Z",
                List.of("ESTUDIANTE", "BRIGADISTA"));
        Evento temprano = evento("Temprano", EstadoEvento.EN_EJECUCION, "2026-11-20T08:00:00Z",
                List.of("ESTUDIANTE"));

        mockMvc.perform(como("BRIGADISTA,ESTUDIANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].evento_id").value(temprano.getId().toString()))
                .andExpect(jsonPath("$[1].evento_id").value(ambos.getId().toString()))
                .andExpect(jsonPath("$[2].evento_id").value(tarde.getId().toString()));
    }

    @Test
    void varioEventosActivosSeDevuelvenTodosOrdenadosPorFechaDeInicio() throws Exception {
        Evento segundo = evento("Segundo", EstadoEvento.EN_EJECUCION, "2026-11-20T11:00:00Z", List.of("PROFESOR"));
        Evento primero = evento("Primero", EstadoEvento.EN_EJECUCION, "2026-11-20T10:00:00Z", List.of("PROFESOR"));

        mockMvc.perform(como("PROFESOR"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].evento_id").value(primero.getId().toString()))
                .andExpect(jsonPath("$[1].evento_id").value(segundo.getId().toString()));
    }

    @Test
    void elEscenarioNoRestringeAQuienLeAplicaElEvento() throws Exception {
        Evento sinEscenario = new Evento(UUID.randomUUID());
        sinEscenario.setNombre("Sin escenario");
        sinEscenario.setSimulacro(false);
        sinEscenario.setEstado(EstadoEvento.EN_EJECUCION);
        sinEscenario.setFechaInicio(Instant.parse("2026-11-20T15:00:00Z"));
        sinEscenario.setRolesConvocados(List.of("ESTUDIANTE"));
        eventos.saveAndFlush(sinEscenario);

        mockMvc.perform(como("ESTUDIANTE"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].escenario.edificios", hasSize(0)))
                .andExpect(jsonPath("$[0].escenario.zonas", hasSize(0)));
    }

    @Test
    void laRespuestaTieneLosCamposDelContratoEnSnakeCase() throws Exception {
        Evento evento = evento("Simulacro de evacuación bloque A", EstadoEvento.EN_EJECUCION,
                "2026-11-20T15:00:00Z", List.of("ESTUDIANTE", "PROFESOR"));

        mockMvc.perform(como("ESTUDIANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].evento_id").value(evento.getId().toString()))
                .andExpect(jsonPath("$[0].nombre").value("Simulacro de evacuación bloque A"))
                .andExpect(jsonPath("$[0].simulacro").value(true))
                .andExpect(jsonPath("$[0].estado").value("EN_EJECUCION"))
                .andExpect(jsonPath("$[0].fecha_inicio").value("2026-11-20T15:00:00Z"))
                .andExpect(jsonPath("$[0].fecha_cierre").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].escenario.edificios[0]").value("Bloque A"))
                .andExpect(jsonPath("$[0].escenario.zonas[0]").value("Zona norte"))
                .andExpect(jsonPath("$[0].roles_convocados[0]").value("ESTUDIANTE"))
                .andExpect(jsonPath("$[0].roles_convocados[1]").value("PROFESOR"))
                .andExpect(jsonPath("$[0].checkin_habilitado").value(true));
    }

    @Test
    void laConsultaNoModificaNingunDato() throws Exception {
        evento("Activo", EstadoEvento.EN_EJECUCION, "2026-11-20T15:00:00Z", List.of("ESTUDIANTE"));
        long antes = eventos.count();

        mockMvc.perform(como("ESTUDIANTE")).andExpect(status().isOk());

        assertEquals(antes, eventos.count());
    }

    @Test
    void elAdminYElAuditorNoTienenPermisoParaConsultarEventosActivos() throws Exception {
        evento("Activo", EstadoEvento.EN_EJECUCION, "2026-11-20T15:00:00Z", List.of("ESTUDIANTE"));

        mockMvc.perform(como("ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        mockMvc.perform(como("AUDITOR")).andExpect(status().isForbidden());
    }

    @Test
    void sinCabecerasDeIdentidadEsNoAutenticado() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }
}
