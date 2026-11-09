package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.config.SecurityConfig;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.config.WebMvcConfig;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.CorrelationIdFilter;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.HistorialService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;

/**
 * Contrato HTTP de RF-MS04-09: GET /api/v1/chi/eventos/{eventoId}/historial con el service real y los
 * repositorios simulados. Comprueba la forma de la respuesta, los filtros, los errores estándar, el permiso
 * ms04:history:read y que el histórico no depende de quién consulta
 */
@WebMvcTest(controllers = HistorialController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, GlobalExceptionHandler.class, CorrelationIdFilter.class,
        HistorialService.class })
@TestPropertySource(properties = "internal.allowed-services=ms06")
class HistorialHttpTest {

    private static final String USUARIO = "6c0d2f48-1b3e-4a57-9d8c-2e4f6a8b0c13";
    private static final UUID EVENTO = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID ANA = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID PUNTO_A = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID EDIFICIO = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
    private static final UUID SIN_USUARIO = null;
    private static final EstadoCheckin SIN_ESTADO = null;
    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
    private static final String BASE = "/api/v1/chi/eventos/" + EVENTO;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventoRepository eventos;

    @MockitoBean
    private CheckinRepository checkins;

    /**
     * Crea un check-in como lo devuelve la base de datos, con el id y la fecha que asigna ella
     *
     * @param usuario usuario que reporta
     * @param estado estado reportado
     * @param punto punto de encuentro, solo con SAFE
     * @param tipo tipo de ubicación, solo con NEEDS_ASSISTANCE
     * @param ubicacion id de la ubicación, solo con NEEDS_ASSISTANCE
     * @param registrado fecha y hora del reporte
     * @return el check-in con su id y su fecha de registro
     */
    private Checkin checkin(UUID usuario, EstadoCheckin estado, UUID punto, TipoUbicacion tipo, UUID ubicacion,
            String registrado) {
        Checkin checkin = new Checkin(EVENTO, usuario, estado, punto, tipo, ubicacion);
        ReflectionTestUtils.setField(checkin, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(checkin, "registradoEn", Instant.parse(registrado));
        return checkin;
    }

    /**
     * Petición de un usuario con el rol indicado
     *
     * @param ruta ruta y consulta de la petición
     * @param rol rol o roles de la cabecera X-User-Role
     * @return la petición con las cabeceras de identidad y de correlación
     */
    private MockHttpServletRequestBuilder como(String ruta, String rol) {
        return get(ruta).header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, rol)
                .header(CorrelationIds.HEADER, CORRELATION);
    }

    /**
     * El histórico responde 200 con los campos del contrato en snake_case: cada reporte con su id, usuario,
     * estado, punto de encuentro o ubicación y fecha, y la paginación de los Estándares
     *
     * @throws Exception si falla la petición
     */
    @Test
    void historialResponde200ConElContratoEnSnakeCase() throws Exception {
        Checkin evacuando = checkin(ANA, EstadoCheckin.EVACUATING, null, null, null, "2026-11-20T15:02:05Z");
        Checkin asistencia = checkin(ANA, EstadoCheckin.NEEDS_ASSISTANCE, null, TipoUbicacion.EDIFICIO, EDIFICIO,
                "2026-11-20T15:04:12Z");
        Checkin aSalvo = checkin(ANA, EstadoCheckin.SAFE, PUNTO_A, null, null, "2026-11-20T15:09:40Z");
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarHistorial(EVENTO, SIN_USUARIO, SIN_ESTADO, PageRequest.of(0, 20))).thenReturn(
                new PageImpl<>(List.of(evacuando, asistencia, aSalvo), PageRequest.of(0, 20), 3));

        mockMvc.perform(como(BASE + "/historial", "COORDINADOR"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIds.HEADER, CORRELATION))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total_elementos").value(3))
                .andExpect(jsonPath("$.total_paginas").value(1))
                .andExpect(jsonPath("$.contenido", hasSize(3)))
                .andExpect(jsonPath("$.contenido[0].checkin_id").value(evacuando.getId().toString()))
                .andExpect(jsonPath("$.contenido[0].usuario_id").value(ANA.toString()))
                .andExpect(jsonPath("$.contenido[0].estado").value("EVACUATING"))
                .andExpect(jsonPath("$.contenido[0].punto_encuentro_id").value(nullValue()))
                .andExpect(jsonPath("$.contenido[0].ubicacion").value(nullValue()))
                .andExpect(jsonPath("$.contenido[0].registrado_en").value("2026-11-20T15:02:05Z"))
                .andExpect(jsonPath("$.contenido[1].estado").value("NEEDS_ASSISTANCE"))
                .andExpect(jsonPath("$.contenido[1].punto_encuentro_id").value(nullValue()))
                .andExpect(jsonPath("$.contenido[1].ubicacion.tipo").value("EDIFICIO"))
                .andExpect(jsonPath("$.contenido[1].ubicacion.id").value(EDIFICIO.toString()))
                .andExpect(jsonPath("$.contenido[1].registrado_en").value("2026-11-20T15:04:12Z"))
                .andExpect(jsonPath("$.contenido[2].estado").value("SAFE"))
                .andExpect(jsonPath("$.contenido[2].punto_encuentro_id").value(PUNTO_A.toString()))
                .andExpect(jsonPath("$.contenido[2].ubicacion").value(nullValue()))
                .andExpect(jsonPath("$.contenido[2].registrado_en").value("2026-11-20T15:09:40Z"));
    }

    /**
     * A4: un evento sin check-ins responde 200 con la lista vacía, total 0 y sin páginas
     *
     * @throws Exception si falla la petición
     */
    @Test
    void historialSinCheckinsResponde200ConLaListaVacia() throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarHistorial(EVENTO, SIN_USUARIO, SIN_ESTADO, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(como(BASE + "/historial", "AUDITOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(0)))
                .andExpect(jsonPath("$.total_elementos").value(0))
                .andExpect(jsonPath("$.total_paginas").value(0));
    }

    /**
     * El usuario, el estado, la página y el tamaño pedidos llegan a la consulta, y el histórico no depende de
     * quién consulta: el usuario filtrado puede ser otro distinto del que pide
     *
     * @throws Exception si falla la petición
     */
    @Test
    void historialUsaLosFiltrosYLaPaginaPedidosYNoLaIdentidadDeQuienConsulta() throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarHistorial(EVENTO, ANA, EstadoCheckin.SAFE, PageRequest.of(2, 5)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 12));

        mockMvc.perform(como(BASE + "/historial?usuario_id=" + ANA + "&estado=SAFE&page=2&size=5", "ORGANIZADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.total_elementos").value(12))
                .andExpect(jsonPath("$.total_paginas").value(3));

        verify(checkins).buscarHistorial(EVENTO, ANA, EstadoCheckin.SAFE, PageRequest.of(2, 5));
    }

    /**
     * Sin filtros el histórico trae todo el evento, con 20 por página
     *
     * @throws Exception si falla la petición
     */
    @Test
    void historialSinFiltrosPideTodoElEventoConVeintePorPagina() throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarHistorial(EVENTO, SIN_USUARIO, SIN_ESTADO, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(como(BASE + "/historial", "COORDINADOR")).andExpect(status().isOk());

        verify(checkins).buscarHistorial(EVENTO, SIN_USUARIO, SIN_ESTADO, PageRequest.of(0, 20));
    }

    /**
     * Cada estado del catálogo se puede usar como filtro
     *
     * @param estado estado del catálogo
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @ValueSource(strings = { "SAFE", "EVACUATING", "NEEDS_ASSISTANCE", "OFF_CAMPUS" })
    void historialAceptaCadaEstadoDelCatalogoComoFiltro(String estado) throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarHistorial(EVENTO, SIN_USUARIO, EstadoCheckin.valueOf(estado), PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(como(BASE + "/historial?estado=" + estado, "COORDINADOR")).andExpect(status().isOk());

        verify(checkins).buscarHistorial(EVENTO, SIN_USUARIO, EstadoCheckin.valueOf(estado), PageRequest.of(0, 20));
    }

    /**
     * Los roles con ms04:history:read pueden consultar el histórico, también con otros roles a la vez
     *
     * @param rol valor de la cabecera X-User-Role
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @ValueSource(strings = { "COORDINADOR", "ORGANIZADOR", "AUDITOR", "BRIGADISTA,COORDINADOR", "AUDITOR,PROFESOR" })
    void losRolesConElPermisoPuedenConsultarElHistorico(String rol) throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarHistorial(EVENTO, SIN_USUARIO, SIN_ESTADO, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(como(BASE + "/historial", rol)).andExpect(status().isOk());
    }

    /**
     * Los roles sin ms04:history:read reciben 403, incluido el BRIGADISTA, y no se consulta la base
     *
     * @param rol valor de la cabecera X-User-Role
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @ValueSource(strings = { "ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO", "BRIGADISTA", "ADMIN", "ESTUDIANTE,PROFESOR",
            "BRIGADISTA,PROFESOR" })
    void losRolesSinElPermisoNoPuedenConsultarElHistorico(String rol) throws Exception {
        mockMvc.perform(como(BASE + "/historial", rol))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"))
                .andExpect(jsonPath("$.errores", hasSize(0)))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));

        verifyNoInteractions(eventos, checkins);
    }

    /**
     * Un rol sin permiso recibe 403 antes que el error de la paginación
     *
     * @throws Exception si falla la petición
     */
    @Test
    void elPermisoSeValidaAntesQueLaPaginacion() throws Exception {
        mockMvc.perform(como(BASE + "/historial?page=-1&size=101", "ESTUDIANTE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));

        verifyNoInteractions(eventos, checkins);
    }

    /**
     * Sin cabeceras de identidad responde 401 NO_AUTENTICADO
     *
     * @throws Exception si falla la petición
     */
    @Test
    void historialSinCabecerasDeIdentidadEsNoAutenticado() throws Exception {
        mockMvc.perform(get(BASE + "/historial").header(CorrelationIds.HEADER, CORRELATION))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));

        verifyNoInteractions(eventos, checkins);
    }

    /**
     * Una paginación inválida responde 400 con el mensaje del formulario F-04 y sin consultar nada
     *
     * @param consulta parámetros de paginación inválidos
     * @param campo parámetro que se reporta
     * @param mensaje mensaje del formulario F-04
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "page=-1|page|Página no válida.",
            "page=abc|page|Página no válida.",
            "size=0|size|Tamaño de página no válido (1 a 100).",
            "size=101|size|Tamaño de página no válido (1 a 100).",
            "size=abc|size|Tamaño de página no válido (1 a 100)." })
    void historialConPaginacionInvalidaResponde400(String consulta, String campo, String mensaje) throws Exception {
        mockMvc.perform(como(BASE + "/historial?" + consulta, "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value(campo))
                .andExpect(jsonPath("$.errores[0].mensaje").value(mensaje));

        verifyNoInteractions(eventos, checkins);
    }

    /**
     * Página y tamaño inválidos a la vez devuelven un error por cada parámetro
     *
     * @throws Exception si falla la petición
     */
    @Test
    void historialConPaginaYTamanoInvalidosDevuelveUnErrorPorParametro() throws Exception {
        mockMvc.perform(como(BASE + "/historial?page=-1&size=101", "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores", hasSize(2)))
                .andExpect(jsonPath("$.errores[0].campo").value("page"))
                .andExpect(jsonPath("$.errores[1].campo").value("size"));
    }

    /**
     * Un usuario que no es UUID responde 400 con el mensaje del formulario F-04
     *
     * @throws Exception si falla la petición
     */
    @Test
    void historialConUnUsuarioInvalidoResponde400() throws Exception {
        mockMvc.perform(como(BASE + "/historial?usuario_id=no-es-un-uuid", "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("usuario_id"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Usuario no válido."));

        verifyNoInteractions(eventos, checkins);
    }

    /**
     * Un estado fuera del catálogo responde 400 con el mensaje del formulario F-04
     *
     * @param estado valor fuera del catálogo
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @ValueSource(strings = { "TODOS", "safe", "FINALIZADO", "123" })
    void historialConUnEstadoInvalidoResponde400(String estado) throws Exception {
        mockMvc.perform(como(BASE + "/historial?estado=" + estado, "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("estado"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Estado no válido."));

        verifyNoInteractions(eventos, checkins);
    }

    /**
     * Un eventoId que no es UUID responde 400 con el mensaje de RG-03
     *
     * @throws Exception si falla la petición
     */
    @Test
    void historialConUnEventoIdInvalidoResponde400() throws Exception {
        mockMvc.perform(como("/api/v1/chi/eventos/no-es-un-uuid/historial", "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("eventoId"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Evento no válido."));

        verifyNoInteractions(eventos, checkins);
    }

    /**
     * A3: un evento inexistente responde 404
     *
     * @throws Exception si falla la petición
     */
    @Test
    void historialDeUnEventoInexistenteResponde404() throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(false);

        mockMvc.perform(como(BASE + "/historial", "COORDINADOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("Evento no encontrado."))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));

        verifyNoInteractions(checkins);
    }
}
