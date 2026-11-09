package edu.eci.dosw.campusseguro.evacuation.evacuation_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.PostgresTestcontainerConfig;

/**
 * RF-MS04-13 Consultar eventos (historia KAIR-604), de punta a punta: las cabeceras del Gateway, el permiso
 * {@code ms04:dashboard:read}, la consulta a PostgreSQL real y el contrato en snake_case. Cada prueba se deshace al
 * terminar. La base de pruebas es compartida con otras clases que dejan eventos guardados, así que las pruebas de la
 * lista miran solo los eventos que crean
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainerConfig.class)
@Transactional
class ConsultaEventoIntegracionTest {

    private static final String BASE = "/api/v1/chi/eventos";
    private static final String USUARIO = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventoRepository eventos;

    private Evento evento(String nombre, EstadoEvento estado, String inicio) {
        Evento evento = new Evento(UUID.randomUUID());
        evento.setNombre(nombre);
        evento.setSimulacro(true);
        evento.setEstado(estado);
        evento.setFechaInicio(inicio == null ? null : Instant.parse(inicio));
        evento.setFechaCierre(estado == EstadoEvento.FINALIZADO ? Instant.parse("2026-11-20T18:00:00Z") : null);
        evento.setRolesConvocados(List.of("ESTUDIANTE", "PROFESOR"));
        evento.setEscenarioEdificios(List.of("Bloque A"));
        evento.setEscenarioZonas(List.of("Zona norte"));
        return eventos.saveAndFlush(evento);
    }

    private static MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder request, String roles) {
        return request.header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, roles);
    }

    /** Recorre todas las páginas de la lista y devuelve los ids en el orden de la respuesta */
    private List<String> idsDeLaLista(String filtro) throws Exception {
        List<String> ids = new ArrayList<>();
        int totalPaginas;
        int page = 0;
        do {
            String json = mockMvc.perform(como(get(BASE + "?size=100&page=" + page + filtro), "COORDINADOR"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            ids.addAll(JsonPath.read(json, "$.contenido[*].evento_id"));
            totalPaginas = JsonPath.read(json, "$.total_paginas");
            page++;
        } while (page < totalPaginas);
        return ids;
    }

    private static List<String> soloEstos(List<String> ids, Evento... propios) {
        List<String> buscados = java.util.Arrays.stream(propios).map(e -> e.getId().toString()).toList();
        return ids.stream().filter(buscados::contains).toList();
    }

    @Test
    void laListaDevuelveLosEventosEnTodosLosEstadosPorFechaDeInicioConLosBorradorAlFinal() throws Exception {
        Evento borrador = evento("Borrador", EstadoEvento.BORRADOR, null);
        Evento antiguo = evento("Finalizado", EstadoEvento.FINALIZADO, "2026-01-10T15:00:00Z");
        Evento reciente = evento("Programado", EstadoEvento.PROGRAMADO, "2099-12-31T15:00:00Z");
        Evento medio = evento("En ejecución", EstadoEvento.EN_EJECUCION, "2098-06-15T15:00:00Z");

        List<String> orden = soloEstos(idsDeLaLista(""), borrador, antiguo, reciente, medio);

        assertEquals(List.of(reciente.getId().toString(), medio.getId().toString(), antiguo.getId().toString(),
                borrador.getId().toString()), orden);
    }

    @Test
    void conLaMismaFechaDeInicioVaPrimeroElRegistradoMasRecientemente() throws Exception {
        Evento primero = evento("Primero", EstadoEvento.PROGRAMADO, "2097-03-01T15:00:00Z");
        Evento segundo = evento("Segundo", EstadoEvento.PROGRAMADO, "2097-03-01T15:00:00Z");

        assertEquals(List.of(segundo.getId().toString(), primero.getId().toString()),
                soloEstos(idsDeLaLista(""), primero, segundo));
    }

    @ParameterizedTest
    @EnumSource(EstadoEvento.class)
    void elFiltroDeEstadoDevuelveSoloLosDeEseEstado(EstadoEvento estado) throws Exception {
        Evento borrador = evento("Borrador", EstadoEvento.BORRADOR, null);
        Evento programado = evento("Programado", EstadoEvento.PROGRAMADO, "2026-11-25T15:00:00Z");
        Evento enEjecucion = evento("En ejecución", EstadoEvento.EN_EJECUCION, "2026-11-20T15:00:00Z");
        Evento finalizado = evento("Finalizado", EstadoEvento.FINALIZADO, "2026-11-10T15:00:00Z");

        String json = mockMvc.perform(como(get(BASE + "?size=100&estado=" + estado), "COORDINADOR"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> estados = JsonPath.read(json, "$.contenido[*].estado");
        assertTrue(estados.stream().allMatch(estado.name()::equals));
        Evento esperado = switch (estado) {
            case BORRADOR -> borrador;
            case PROGRAMADO -> programado;
            case EN_EJECUCION -> enEjecucion;
            case FINALIZADO -> finalizado;
        };
        List<String> ids = idsDeLaLista("&estado=" + estado);
        assertEquals(List.of(esperado.getId().toString()),
                soloEstos(ids, borrador, programado, enEjecucion, finalizado));
    }

    @Test
    void laListaUsaLaPaginacionDeLosEstandaresYElContratoEnSnakeCase() throws Exception {
        evento("Programado", EstadoEvento.PROGRAMADO, "2026-11-25T15:00:00Z");

        mockMvc.perform(como(get(BASE + "?estado=PROGRAMADO&page=0&size=1"), "AUDITOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total_elementos").isNumber())
                .andExpect(jsonPath("$.total_paginas").isNumber())
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].evento_id").isNotEmpty())
                .andExpect(jsonPath("$.contenido[0].roles_convocados").isArray())
                .andExpect(jsonPath("$.contenido[0].checkin_habilitado").value(false));
    }

    @Test
    void unaPaginaSinEventosEsUnaListaVacia() throws Exception {
        mockMvc.perform(como(get(BASE + "?page=100000"), "COORDINADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(0));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoEvento.class, names = { "BORRADOR", "PROGRAMADO", "FINALIZADO" })
    void unEventoConElCheckinDeshabilitadoSeConsultaPorSuId(EstadoEvento estado) throws Exception {
        String inicio = estado == EstadoEvento.BORRADOR ? null : "2026-11-20T15:00:00Z";
        Evento evento = evento("Simulacro bloque A", estado, inicio);

        mockMvc.perform(como(get(BASE + "/" + evento.getId()), "COORDINADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evento_id").value(evento.getId().toString()))
                .andExpect(jsonPath("$.nombre").value("Simulacro bloque A"))
                .andExpect(jsonPath("$.simulacro").value(true))
                .andExpect(jsonPath("$.estado").value(estado.name()))
                .andExpect(jsonPath("$.checkin_habilitado").value(false))
                .andExpect(jsonPath("$.escenario.edificios[0]").value("Bloque A"))
                .andExpect(jsonPath("$.escenario.zonas[0]").value("Zona norte"))
                .andExpect(jsonPath("$.roles_convocados[0]").value("ESTUDIANTE"));
    }

    @Test
    void unEventoFinalizadoTraeSuFechaDeCierreYUnoEnEjecucionElCheckinHabilitado() throws Exception {
        Evento finalizado = evento("Finalizado", EstadoEvento.FINALIZADO, "2026-11-20T15:00:00Z");
        Evento enEjecucion = evento("En ejecución", EstadoEvento.EN_EJECUCION, "2026-11-20T15:00:00Z");

        mockMvc.perform(como(get(BASE + "/" + finalizado.getId()), "ORGANIZADOR"))
                .andExpect(jsonPath("$.fecha_inicio").value("2026-11-20T15:00:00Z"))
                .andExpect(jsonPath("$.fecha_cierre").value("2026-11-20T18:00:00Z"));
        mockMvc.perform(como(get(BASE + "/" + enEjecucion.getId()), "ORGANIZADOR"))
                .andExpect(jsonPath("$.fecha_cierre").doesNotExist())
                .andExpect(jsonPath("$.checkin_habilitado").value(true));
    }

    @Test
    void unEventoInexistenteEs404() throws Exception {
        mockMvc.perform(como(get(BASE + "/" + UUID.randomUUID()), "COORDINADOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("Evento no encontrado."));
    }

    @Test
    void elBrigadistaVeTodosLosEventosSinFiltroDeZona() throws Exception {
        Evento evento = evento("Otro edificio", EstadoEvento.PROGRAMADO, "2026-11-25T15:00:00Z");

        mockMvc.perform(como(get(BASE + "/" + evento.getId()), "BRIGADISTA"))
                .andExpect(status().isOk());
        assertTrue(Set.copyOf(idsDeLaLista("")).contains(evento.getId().toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO", "ADMIN" })
    void unRolSinDashboardReadRecibe403(String rol) throws Exception {
        Evento evento = evento("Programado", EstadoEvento.PROGRAMADO, "2026-11-25T15:00:00Z");

        mockMvc.perform(como(get(BASE), rol)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        mockMvc.perform(como(get(BASE + "/" + evento.getId()), rol)).andExpect(status().isForbidden());
    }

    @Test
    void losFiltrosInvalidosSon400ConLosMensajesDeF07() throws Exception {
        mockMvc.perform(como(get(BASE + "?estado=CANCELADO"), "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("estado"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Estado de evento no válido."));
        mockMvc.perform(como(get(BASE + "?page=-1"), "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Página no válida."));
        mockMvc.perform(como(get(BASE + "?size=0"), "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Tamaño de página no válido (1 a 100)."));
    }

    @Test
    void ningunaRutaDeUsuarioPermiteCrearModificarNiEliminarUnEvento() throws Exception {
        Evento evento = evento("Programado", EstadoEvento.PROGRAMADO, "2026-11-25T15:00:00Z");
        String ruta = BASE + "/" + evento.getId();
        String cuerpo = "{\"nombre\":\"Cambiado\"}";

        mockMvc.perform(como(post(BASE), "COORDINADOR").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(como(put(ruta), "COORDINADOR").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(como(patch(ruta), "COORDINADOR").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(como(delete(ruta), "COORDINADOR")).andExpect(status().isMethodNotAllowed());
        // El cambio de estado es la ruta interna de MS06: un usuario no puede usarla
        mockMvc.perform(como(put(ruta + "/estado"), "COORDINADOR").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo)).andExpect(status().isUnauthorized());

        assertEquals("Programado", eventos.findById(evento.getId()).orElseThrow().getNombre());
    }
}
