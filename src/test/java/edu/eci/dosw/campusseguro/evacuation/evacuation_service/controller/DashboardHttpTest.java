package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.PoblacionObjetivoClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.UsuarioNombresClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ZonasBrigadistaClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ZonasBrigadistaClient.ZonasAsignadas;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.config.SecurityConfig;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.config.WebMvcConfig;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEnvio;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.SolicitudAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository.ConteoPorEstadoYPunto;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.SolicitudAsistenciaRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.CorrelationIdFilter;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Rol;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.DashboardService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;

/**
 * Contrato HTTP de RF-MS04-06, RF-MS04-07 y RF-MS04-08: GET /api/v1/chi/eventos/{eventoId}/consolidado, /personas,
 * /pendientes y /asistencia con el service real y los repositorios y clientes de MS01 y MS05 simulados. Comprueba
 * la forma de las respuestas, los errores estándar, el permiso ms04:dashboard:read y que la identidad sale solo de
 * las cabeceras
 */
@WebMvcTest(controllers = DashboardController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, GlobalExceptionHandler.class, CorrelationIdFilter.class,
        DashboardService.class, DashboardHttpTest.RelojFijo.class })
@TestPropertySource(properties = "internal.allowed-services=ms06")
class DashboardHttpTest {

    private static final String USUARIO = "6c0d2f48-1b3e-4a57-9d8c-2e4f6a8b0c13";
    private static final UUID EVENTO = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID ANA = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID LUIS = UUID.fromString("5d2e8f10-6a4b-4c93-b7e1-3f9a0c2d4e68");
    private static final UUID PUNTO_A = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID PUNTO_B = UUID.fromString("d3f5a7c9-1b2e-4d6f-8a0c-4e6b8d0f2a41");
    private static final Instant REGISTRADO = Instant.parse("2026-11-20T15:04:12Z");
    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
    private static final String BASE = "/api/v1/chi/eventos/" + EVENTO;
    private static final Set<UUID> NADA = Set.of();

    /** Reloj fijo para comprobar la fecha del cálculo */
    @TestConfiguration(proxyBeanMethods = false)
    static class RelojFijo {

        /**
         * Reloj que siempre marca la misma hora
         *
         * @return el reloj fijo en UTC
         */
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-20T15:10:00Z"), ZoneOffset.UTC);
        }
    }

    /** Fila de conteo como la devuelve la base de datos */
    private record Fila(String estado, UUID punto, long total) implements ConteoPorEstadoYPunto {

        @Override
        public String getEstado() {
            return estado;
        }

        @Override
        public UUID getPunto() {
            return punto;
        }

        @Override
        public long getTotal() {
            return total;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventoRepository eventos;

    @MockitoBean
    private CheckinRepository checkins;

    @MockitoBean
    private SolicitudAsistenciaRepository solicitudes;

    @MockitoBean
    private UsuarioNombresClient nombres;

    @MockitoBean
    private ZonasBrigadistaClient zonasBrigadista;

    @MockitoBean
    private PoblacionObjetivoClient poblacionObjetivo;

    /**
     * Crea el evento en el estado indicado
     *
     * @param estado estado del evento
     * @return el evento
     */
    private Evento evento(EstadoEvento estado) {
        Evento evento = new Evento(EVENTO);
        evento.setEstado(estado);
        return evento;
    }

    /**
     * Crea un check-in como lo devuelve la base de datos, con la fecha que asigna ella
     *
     * @param usuario usuario que reporta
     * @param estado estado reportado
     * @param punto punto de encuentro, solo con SAFE
     * @return el check-in con su fecha de registro
     */
    private Checkin checkin(UUID usuario, EstadoCheckin estado, UUID punto) {
        Checkin checkin = new Checkin(EVENTO, usuario, estado, punto, null, null);
        ReflectionTestUtils.setField(checkin, "registradoEn", REGISTRADO);
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

    // ---------------------------------------------------------------- consolidado

    /**
     * El consolidado responde 200 con los campos del contrato en snake_case: los cuatro estados, los puntos de
     * encuentro y la fecha del cálculo
     *
     * @throws Exception si falla la petición
     */
    @Test
    void consolidadoResponde200ConElContratoEnSnakeCase() throws Exception {
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(evento(EstadoEvento.EN_EJECUCION)));
        when(checkins.contarVigentes(EVENTO, false, NADA, NADA, NADA)).thenReturn(List.of(
                new Fila("SAFE", PUNTO_A, 120), new Fila("SAFE", PUNTO_B, 30), new Fila("EVACUATING", null, 35),
                new Fila("NEEDS_ASSISTANCE", null, 4), new Fila("OFF_CAMPUS", null, 18)));

        mockMvc.perform(como(BASE + "/consolidado", "COORDINADOR"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIds.HEADER, CORRELATION))
                .andExpect(jsonPath("$.evento_id").value(EVENTO.toString()))
                .andExpect(jsonPath("$.estado_evento").value("EN_EJECUCION"))
                .andExpect(jsonPath("$.total_reportados").value(207))
                .andExpect(jsonPath("$.totales_por_estado.SAFE").value(150))
                .andExpect(jsonPath("$.totales_por_estado.EVACUATING").value(35))
                .andExpect(jsonPath("$.totales_por_estado.NEEDS_ASSISTANCE").value(4))
                .andExpect(jsonPath("$.totales_por_estado.OFF_CAMPUS").value(18))
                .andExpect(jsonPath("$.por_punto_encuentro", hasSize(2)))
                .andExpect(jsonPath("$.por_punto_encuentro[0].punto_encuentro_id").value(PUNTO_A.toString()))
                .andExpect(jsonPath("$.por_punto_encuentro[0].total").value(120))
                .andExpect(jsonPath("$.por_punto_encuentro[1].punto_encuentro_id").value(PUNTO_B.toString()))
                .andExpect(jsonPath("$.calculado_en").value("2026-11-20T15:10:00Z"));
    }

    /**
     * Un evento sin check-ins responde los cuatro estados en 0 y la lista de puntos vacía
     *
     * @throws Exception si falla la petición
     */
    @Test
    void consolidadoSinCheckinsResponde200ConLosCuatroEstadosEnCero() throws Exception {
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(evento(EstadoEvento.PROGRAMADO)));
        when(checkins.contarVigentes(EVENTO, false, NADA, NADA, NADA)).thenReturn(List.of());

        mockMvc.perform(como(BASE + "/consolidado", "ORGANIZADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado_evento").value("PROGRAMADO"))
                .andExpect(jsonPath("$.total_reportados").value(0))
                .andExpect(jsonPath("$.totales_por_estado.SAFE").value(0))
                .andExpect(jsonPath("$.totales_por_estado.EVACUATING").value(0))
                .andExpect(jsonPath("$.totales_por_estado.NEEDS_ASSISTANCE").value(0))
                .andExpect(jsonPath("$.totales_por_estado.OFF_CAMPUS").value(0))
                .andExpect(jsonPath("$.por_punto_encuentro", hasSize(0)));
    }

    /**
     * Los roles con ms04:dashboard:read pueden consolidar el evento, incluido el BRIGADISTA con sus zonas
     *
     * @param rol valor de la cabecera X-User-Role
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @ValueSource(strings = { "COORDINADOR", "ORGANIZADOR", "AUDITOR", "BRIGADISTA", "BRIGADISTA,COORDINADOR" })
    void losRolesConElPermisoPuedenConsolidar(String rol) throws Exception {
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(evento(EstadoEvento.EN_EJECUCION)));
        when(zonasBrigadista.zonasDe(any())).thenReturn(new ZonasAsignadas(NADA, NADA, NADA));
        when(checkins.contarVigentes(EVENTO, false, NADA, NADA, NADA)).thenReturn(List.of());
        when(checkins.contarVigentes(EVENTO, true, NADA, NADA, NADA)).thenReturn(List.of());

        mockMvc.perform(como(BASE + "/consolidado", rol)).andExpect(status().isOk());
    }

    /**
     * Los roles sin ms04:dashboard:read reciben 403 y no se consulta la base
     *
     * @param rol valor de la cabecera X-User-Role
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @ValueSource(strings = { "ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO", "ADMIN", "ESTUDIANTE,PROFESOR" })
    void losRolesSinElPermisoNoPuedenConsolidar(String rol) throws Exception {
        mockMvc.perform(como(BASE + "/consolidado", rol))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"))
                .andExpect(jsonPath("$.errores", hasSize(0)))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));

        verifyNoInteractions(eventos, checkins, nombres, zonasBrigadista);
    }

    /**
     * Sin cabeceras de identidad responde 401 NO_AUTENTICADO
     *
     * @throws Exception si falla la petición
     */
    @Test
    void consolidadoSinCabecerasDeIdentidadEsNoAutenticado() throws Exception {
        mockMvc.perform(get(BASE + "/consolidado").header(CorrelationIds.HEADER, CORRELATION))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));

        verifyNoInteractions(eventos, checkins);
    }

    /**
     * Un evento inexistente responde 404 NO_ENCONTRADO
     *
     * @throws Exception si falla la petición
     */
    @Test
    void consolidadoDeUnEventoInexistenteResponde404() throws Exception {
        when(eventos.findById(EVENTO)).thenReturn(Optional.empty());

        mockMvc.perform(como(BASE + "/consolidado", "COORDINADOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("Evento no encontrado."))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));
    }

    /**
     * Si MS05 no entrega las zonas del BRIGADISTA responde 503 con el ErrorResponse propio de MS04
     *
     * @throws Exception si falla la petición
     */
    @Test
    void consolidadoDelBrigadistaSinZonasDeMs05Responde503() throws Exception {
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(evento(EstadoEvento.EN_EJECUCION)));
        when(zonasBrigadista.zonasDe(any())).thenThrow(ApiException.dependenciaNoDisponible());

        mockMvc.perform(como(BASE + "/consolidado", "BRIGADISTA"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.codigo").value("DEPENDENCIA_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));

        verifyNoInteractions(checkins);
    }

    /**
     * El BRIGADISTA consolida con las zonas que MS05 le entrega y el id de usuario sale de la cabecera
     *
     * @throws Exception si falla la petición
     */
    @Test
    void consolidadoDelBrigadistaUsaSusZonasYSuIdentidadDeLasCabeceras() throws Exception {
        UUID edificio = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(evento(EstadoEvento.EN_EJECUCION)));
        when(zonasBrigadista.zonasDe(any())).thenReturn(new ZonasAsignadas(Set.of(edificio), NADA, Set.of(PUNTO_A)));
        when(checkins.contarVigentes(EVENTO, true, Set.of(PUNTO_A), Set.of(edificio), NADA))
                .thenReturn(List.of(new Fila("SAFE", PUNTO_A, 6), new Fila("NEEDS_ASSISTANCE", null, 1)));

        mockMvc.perform(como(BASE + "/consolidado", "BRIGADISTA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totales_por_estado.SAFE").value(6))
                .andExpect(jsonPath("$.totales_por_estado.NEEDS_ASSISTANCE").value(1))
                .andExpect(jsonPath("$.totales_por_estado.EVACUATING").value(0))
                .andExpect(jsonPath("$.totales_por_estado.OFF_CAMPUS").value(0))
                .andExpect(jsonPath("$.total_reportados").value(7));

        verify(zonasBrigadista).zonasDe(new RequesterIdentity(UUID.fromString(USUARIO), EnumSet.of(Rol.BRIGADISTA)));
    }

    // ---------------------------------------------------------------- personas

    /**
     * El listado responde 200 con la página en el formato de los Estándares y los campos del contrato en
     * snake_case
     *
     * @throws Exception si falla la petición
     */
    @Test
    void personasResponde200ConLaPaginaYElContrato() throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigentes(EVENTO, EstadoCheckin.SAFE, PUNTO_A, false, NADA, NADA, NADA,
                PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(checkin(ANA, EstadoCheckin.SAFE, PUNTO_A)),
                        PageRequest.of(0, 20), 1));
        when(nombres.nombresDe(List.of(ANA))).thenReturn(Map.of(ANA, "Ana Pérez"));

        mockMvc.perform(como(BASE + "/personas?estado=SAFE&punto_encuentro_id=" + PUNTO_A, "AUDITOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].usuario_id").value(ANA.toString()))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.contenido[0].estado").value("SAFE"))
                .andExpect(jsonPath("$.contenido[0].punto_encuentro_id").value(PUNTO_A.toString()))
                .andExpect(jsonPath("$.contenido[0].fecha_hora").value("2026-11-20T15:04:12Z"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total_elementos").value(1))
                .andExpect(jsonPath("$.total_paginas").value(1));
    }

    /**
     * Sin filtros ni paginación usa la página 0 con 20 elementos, y si MS01 no entrega el nombre este sale nulo
     *
     * @throws Exception si falla la petición
     */
    @Test
    void personasSinParametrosUsaLaPaginaPorDefectoYSinNombreSaleNulo() throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigentes(EVENTO, null, null, false, NADA, NADA, NADA, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(checkin(LUIS, EstadoCheckin.EVACUATING, null)),
                        PageRequest.of(0, 20), 1));
        when(nombres.nombresDe(List.of(LUIS))).thenReturn(Map.of());

        mockMvc.perform(como(BASE + "/personas", "COORDINADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].usuario_id").value(LUIS.toString()))
                .andExpect(jsonPath("$.contenido[0].nombre").value(nullValue()))
                .andExpect(jsonPath("$.contenido[0].punto_encuentro_id").value(nullValue()))
                .andExpect(jsonPath("$.size").value(20));
    }

    /**
     * Los roles sin ms04:dashboard:read reciben 403 en el listado
     *
     * @param rol valor de la cabecera X-User-Role
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @ValueSource(strings = { "ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO", "ADMIN" })
    void losRolesSinElPermisoNoPuedenListarPersonas(String rol) throws Exception {
        mockMvc.perform(como(BASE + "/personas", rol))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));

        verifyNoInteractions(eventos, checkins, nombres, zonasBrigadista);
    }

    /**
     * Sin cabeceras de identidad el listado responde 401
     *
     * @throws Exception si falla la petición
     */
    @Test
    void personasSinCabecerasDeIdentidadEsNoAutenticado() throws Exception {
        mockMvc.perform(get(BASE + "/personas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    /**
     * El punto de encuentro con un estado distinto de SAFE responde 400 con el mensaje del formulario F-02
     *
     * @param estado estado distinto de SAFE
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @ValueSource(strings = { "EVACUATING", "NEEDS_ASSISTANCE", "OFF_CAMPUS" })
    void personasConPuntoYEstadoDistintoDeSafeResponde400(String estado) throws Exception {
        mockMvc.perform(como(BASE + "/personas?estado=" + estado + "&punto_encuentro_id=" + PUNTO_A,
                "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("punto_encuentro_id"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El punto de encuentro solo aplica a Estoy a salvo."));

        verifyNoInteractions(eventos, checkins);
    }

    /**
     * Una paginación inválida responde 400 con un error por cada parámetro
     *
     * @param consulta parámetros de paginación inválidos
     * @param campo parámetro que se reporta
     * @param mensaje mensaje del formulario F-02
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "page=-1|page|Página no válida.",
            "size=0|size|Tamaño de página no válido (1 a 100).",
            "size=101|size|Tamaño de página no válido (1 a 100)." })
    void personasConPaginacionInvalidaResponde400(String consulta, String campo, String mensaje) throws Exception {
        mockMvc.perform(como(BASE + "/personas?" + consulta, "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value(campo))
                .andExpect(jsonPath("$.errores[0].mensaje").value(mensaje));

        verifyNoInteractions(eventos, checkins);
    }

    /**
     * Un estado, un punto de encuentro, un número de página o un tamaño con formato inválido responden 400 con el
     * mensaje del formulario F-02
     *
     * @param consulta parámetros de consulta con formato inválido
     * @param campo parámetro que se reporta
     * @param mensaje mensaje del formulario F-02
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "estado=OTRO|estado|Estado no válido.",
            "punto_encuentro_id=3|punto_encuentro_id|Punto de encuentro no válido.",
            "page=abc|page|Página no válida.",
            "size=abc|size|Tamaño de página no válido (1 a 100)." })
    void personasConFormatoInvalidoResponde400ConElMensajeDeF02(String consulta, String campo, String mensaje)
            throws Exception {
        mockMvc.perform(como(BASE + "/personas?" + consulta, "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value(campo))
                .andExpect(jsonPath("$.errores[0].mensaje").value(mensaje));

        verifyNoInteractions(eventos, checkins);
    }

    /**
     * Un eventoId que no es UUID responde 400 con el mensaje de RG-03
     *
     * @throws Exception si falla la petición
     */
    @Test
    void unEventoIdInvalidoResponde400ConElMensajeDeRg03() throws Exception {
        mockMvc.perform(como("/api/v1/chi/eventos/no-es-un-uuid/consolidado", "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("eventoId"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Evento no válido."));
    }

    /**
     * Un evento inexistente responde 404 en el listado
     *
     * @throws Exception si falla la petición
     */
    @Test
    void personasDeUnEventoInexistenteResponde404() throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(false);

        mockMvc.perform(como(BASE + "/personas", "COORDINADOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("Evento no encontrado."));
    }

    /**
     * Si MS05 no entrega las zonas del BRIGADISTA, el listado responde 503
     *
     * @throws Exception si falla la petición
     */
    @Test
    void personasDelBrigadistaSinZonasDeMs05Responde503() throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(zonasBrigadista.zonasDe(any())).thenThrow(ApiException.dependenciaNoDisponible());

        mockMvc.perform(como(BASE + "/personas", "BRIGADISTA"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("DEPENDENCIA_NO_DISPONIBLE"));

        verifyNoInteractions(checkins, nombres);
    }

    // ---------------------------------------------------------------- pendientes

    private static final List<String> ROLES = List.of("ESTUDIANTE", "PROFESOR");
    private static final Instant INICIO = Instant.parse("2026-11-20T14:00:00Z");

    /**
     * Crea un evento con los roles convocados y el inicio fijos
     *
     * @return el evento EN_EJECUCION
     */
    private Evento eventoConvocado() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        evento.setRolesConvocados(ROLES);
        evento.setFechaInicio(INICIO);
        return evento;
    }

    /**
     * Los pendientes responden 200 con la página de los Estándares y los campos del contrato en snake_case, con el
     * total de pendientes en total_elementos
     *
     * @throws Exception si falla la petición
     */
    @Test
    void pendientesResponde200ConLaPaginaYElContrato() throws Exception {
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(eventoConvocado()));
        when(poblacionObjetivo.usuariosActivosPorRoles(any(), any(), any()))
                .thenReturn(Optional.of(Set.of(ANA, LUIS)));
        when(checkins.buscarUsuariosConEstadoVigente(EVENTO)).thenReturn(List.of());
        when(nombres.nombresDe(List.of(ANA, LUIS))).thenReturn(Map.of(ANA, "Ana Pérez"));

        mockMvc.perform(como(BASE + "/pendientes", "COORDINADOR"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIds.HEADER, CORRELATION))
                .andExpect(jsonPath("$.contenido", hasSize(2)))
                .andExpect(jsonPath("$.contenido[0].usuario_id").value(ANA.toString()))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.contenido[1].usuario_id").value(LUIS.toString()))
                .andExpect(jsonPath("$.contenido[1].nombre").value(nullValue()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total_elementos").value(2))
                .andExpect(jsonPath("$.total_paginas").value(1));
    }

    /**
     * Quien ya reportó no aparece, y el total de pendientes cuenta solo a quienes faltan
     *
     * @throws Exception si falla la petición
     */
    @Test
    void quienYaReportoNoApareceEnLosPendientes() throws Exception {
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(eventoConvocado()));
        when(poblacionObjetivo.usuariosActivosPorRoles(any(), any(), any()))
                .thenReturn(Optional.of(Set.of(ANA, LUIS)));
        when(checkins.buscarUsuariosConEstadoVigente(EVENTO)).thenReturn(List.of(ANA));
        when(nombres.nombresDe(List.of(LUIS))).thenReturn(Map.of(LUIS, "Luis Gómez"));

        mockMvc.perform(como(BASE + "/pendientes", "AUDITOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].usuario_id").value(LUIS.toString()))
                .andExpect(jsonPath("$.total_elementos").value(1));
    }

    /**
     * Pide la página indicada: el contenido es solo esa parte y el total y las páginas son los de toda la lista
     *
     * @throws Exception si falla la petición
     */
    @Test
    void pendientesRespetaLaPaginaYElTamanoPedidos() throws Exception {
        Set<UUID> poblacion = new HashSet<>();
        for (int i = 1; i <= 25; i++) {
            poblacion.add(UUID.fromString(String.format("00000000-0000-4000-8000-%012d", i)));
        }
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(eventoConvocado()));
        when(poblacionObjetivo.usuariosActivosPorRoles(any(), any(), any())).thenReturn(Optional.of(poblacion));
        when(checkins.buscarUsuariosConEstadoVigente(EVENTO)).thenReturn(List.of());
        when(nombres.nombresDe(any())).thenReturn(Map.of());

        mockMvc.perform(como(BASE + "/pendientes?page=2&size=10", "ORGANIZADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(5)))
                .andExpect(jsonPath("$.contenido[0].usuario_id").value("00000000-0000-4000-8000-000000000021"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.total_elementos").value(25))
                .andExpect(jsonPath("$.total_paginas").value(3));
    }

    /**
     * Los roles con el permiso, solos o junto con BRIGADISTA, pueden consultar los pendientes
     *
     * @param rol valor de la cabecera X-User-Role
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @ValueSource(strings = { "COORDINADOR", "ORGANIZADOR", "AUDITOR", "BRIGADISTA,COORDINADOR" })
    void losRolesConElPermisoPuedenConsultarPendientes(String rol) throws Exception {
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(eventoConvocado()));
        when(poblacionObjetivo.usuariosActivosPorRoles(any(), any(), any())).thenReturn(Optional.of(Set.of(ANA)));
        when(checkins.buscarUsuariosConEstadoVigente(EVENTO)).thenReturn(List.of());
        when(nombres.nombresDe(any())).thenReturn(Map.of());

        mockMvc.perform(como(BASE + "/pendientes", rol))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elementos").value(1));
    }

    /**
     * El BRIGADISTA recibe 200 con total 0 y lista vacía, sin consultar a MS01, a MS05 ni los check-ins (A6)
     *
     * @throws Exception si falla la petición
     */
    @Test
    void elBrigadistaRecibe200ConTotalCeroYListaVacia() throws Exception {
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(eventoConvocado()));

        mockMvc.perform(como(BASE + "/pendientes", "BRIGADISTA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", empty()))
                .andExpect(jsonPath("$.total_elementos").value(0))
                .andExpect(jsonPath("$.total_paginas").value(0));

        verifyNoInteractions(poblacionObjetivo, zonasBrigadista, checkins, nombres);
    }

    /**
     * Un rol sin ms04:dashboard:read recibe 403 sin consultar nada
     *
     * @param rol valor de la cabecera X-User-Role
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @ValueSource(strings = { "ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO", "ADMIN", "ESTUDIANTE,PROFESOR" })
    void losRolesSinElPermisoNoPuedenConsultarPendientes(String rol) throws Exception {
        mockMvc.perform(como(BASE + "/pendientes", rol))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));

        verifyNoInteractions(eventos, checkins, nombres, zonasBrigadista, poblacionObjetivo);
    }

    /**
     * Sin cabeceras de identidad los pendientes responden 401
     *
     * @throws Exception si falla la petición
     */
    @Test
    void pendientesSinCabecerasDeIdentidadEsNoAutenticado() throws Exception {
        mockMvc.perform(get(BASE + "/pendientes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    /**
     * Una paginación inválida responde 400 con el mensaje del formulario F-03 y sin consultar nada
     *
     * @param consulta parámetros de paginación inválidos
     * @param campo parámetro que se reporta
     * @param mensaje mensaje del formulario F-03
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "page=-1|page|Página no válida.",
            "page=abc|page|Página no válida.",
            "size=0|size|Tamaño de página no válido (1 a 100).",
            "size=101|size|Tamaño de página no válido (1 a 100).",
            "size=abc|size|Tamaño de página no válido (1 a 100)." })
    void pendientesConPaginacionInvalidaResponde400(String consulta, String campo, String mensaje)
            throws Exception {
        mockMvc.perform(como(BASE + "/pendientes?" + consulta, "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value(campo))
                .andExpect(jsonPath("$.errores[0].mensaje").value(mensaje));

        verifyNoInteractions(eventos, checkins, nombres, poblacionObjetivo);
    }

    /**
     * Un evento inexistente responde 404
     *
     * @throws Exception si falla la petición
     */
    @Test
    void pendientesDeUnEventoInexistenteResponde404() throws Exception {
        when(eventos.findById(EVENTO)).thenReturn(Optional.empty());

        mockMvc.perform(como(BASE + "/pendientes", "COORDINADOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("Evento no encontrado."));

        verifyNoInteractions(poblacionObjetivo, checkins, nombres);
    }

    /**
     * Si MS01 no entrega la población objetivo, los pendientes responden 503 con el mensaje de F-03 y el
     * consolidado del mismo evento sigue funcionando
     *
     * @throws Exception si falla la petición
     */
    @Test
    void sinPoblacionDeMs01LosPendientesResponden503YElConsolidadoSigueFuncionando() throws Exception {
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(eventoConvocado()));
        when(poblacionObjetivo.usuariosActivosPorRoles(any(), any(), any())).thenReturn(Optional.empty());
        when(checkins.contarVigentes(EVENTO, false, NADA, NADA, NADA)).thenReturn(List.of());

        mockMvc.perform(como(BASE + "/pendientes", "COORDINADOR"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.codigo").value("DEPENDENCIA_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.mensaje").value("La lista de pendientes no está disponible en este momento."))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));
        mockMvc.perform(como(BASE + "/consolidado", "COORDINADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_reportados").value(0));

        verifyNoInteractions(nombres);
    }

    /**
     * La consulta a MS01 lleva los roles convocados, el corte de creación y la identidad que sale de las cabeceras
     *
     * @throws Exception si falla la petición
     */
    @Test
    void laPoblacionSePideConLosRolesElCorteYLaIdentidadDeLasCabeceras() throws Exception {
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(eventoConvocado()));
        when(poblacionObjetivo.usuariosActivosPorRoles(any(), any(), any())).thenReturn(Optional.of(Set.of()));
        when(checkins.buscarUsuariosConEstadoVigente(EVENTO)).thenReturn(List.of());
        when(nombres.nombresDe(any())).thenReturn(Map.of());

        mockMvc.perform(como(BASE + "/pendientes", "AUDITOR")).andExpect(status().isOk());

        verify(poblacionObjetivo).usuariosActivosPorRoles(ROLES, INICIO,
                new RequesterIdentity(UUID.fromString(USUARIO), EnumSet.of(Rol.AUDITOR)));
    }

    // ---------------------------------------------------------------- asistencia

    private static final UUID EDIFICIO = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
    private static final UUID ESPACIO = UUID.fromString("d2a8c6e0-4f1b-4735-9e3d-5a7c9b1f3e60");

    /**
     * Crea el check-in vigente Requiero asistencia de un usuario, como lo devuelve la base de datos
     *
     * @param usuario usuario que reporta
     * @param tipo tipo de la ubicación reportada
     * @param ubicacion id de MS02 de la ubicación
     * @return el check-in con su fecha de registro
     */
    private Checkin checkinAsistencia(UUID usuario, TipoUbicacion tipo, UUID ubicacion) {
        Checkin checkin = new Checkin(EVENTO, usuario, EstadoCheckin.NEEDS_ASSISTANCE, null, tipo, ubicacion);
        ReflectionTestUtils.setField(checkin, "registradoEn", REGISTRADO);
        return checkin;
    }

    /**
     * Crea la solicitud de asistencia de un usuario con su estado de envío
     *
     * @param usuario usuario de la solicitud
     * @param solicitudMs05 id que devolvió MS05, null si no llegó
     * @param estadoEnvio estado de envío
     * @return la solicitud
     */
    private SolicitudAsistencia solicitud(UUID usuario, String solicitudMs05, EstadoEnvio estadoEnvio) {
        SolicitudAsistencia solicitud = new SolicitudAsistencia(UUID.randomUUID(), EVENTO, usuario,
                TipoUbicacion.EDIFICIO, EDIFICIO, REGISTRADO);
        solicitud.setSolicitudMs05Id(solicitudMs05);
        solicitud.setEstadoEnvio(estadoEnvio);
        return solicitud;
    }

    /**
     * La lista responde 200 con la página de los Estándares y los campos del contrato en snake_case: la ubicación,
     * la fecha del check-in vigente y la solicitud con el id de MS05 y el estado de su envío
     *
     * @throws Exception si falla la petición
     */
    @Test
    void asistenciaResponde200ConLaPaginaYElContrato() throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigentesConAsistencia(EVENTO, false, NADA, NADA, NADA, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(checkinAsistencia(ANA, TipoUbicacion.EDIFICIO, EDIFICIO),
                        checkinAsistencia(LUIS, TipoUbicacion.ESPACIO, ESPACIO)), PageRequest.of(0, 20), 2));
        when(nombres.nombresDe(List.of(ANA, LUIS))).thenReturn(Map.of(ANA, "Ana Pérez"));
        when(solicitudes.findByEventoIdAndUsuarioIdIn(EVENTO, List.of(ANA, LUIS))).thenReturn(List.of(
                solicitud(ANA, "SOL-2026-0042", EstadoEnvio.ENVIADA),
                solicitud(LUIS, null, EstadoEnvio.PENDIENTE_ENVIO)));

        mockMvc.perform(como(BASE + "/asistencia", "COORDINADOR"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIds.HEADER, CORRELATION))
                .andExpect(jsonPath("$.contenido", hasSize(2)))
                .andExpect(jsonPath("$.contenido[0].usuario_id").value(ANA.toString()))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.contenido[0].ubicacion.tipo").value("EDIFICIO"))
                .andExpect(jsonPath("$.contenido[0].ubicacion.id").value(EDIFICIO.toString()))
                .andExpect(jsonPath("$.contenido[0].fecha_reporte").value("2026-11-20T15:04:12Z"))
                .andExpect(jsonPath("$.contenido[0].solicitud_asistencia.id").value("SOL-2026-0042"))
                .andExpect(jsonPath("$.contenido[0].solicitud_asistencia.estado_envio").value("ENVIADA"))
                .andExpect(jsonPath("$.contenido[1].usuario_id").value(LUIS.toString()))
                .andExpect(jsonPath("$.contenido[1].nombre").value(nullValue()))
                .andExpect(jsonPath("$.contenido[1].ubicacion.tipo").value("ESPACIO"))
                .andExpect(jsonPath("$.contenido[1].ubicacion.id").value(ESPACIO.toString()))
                .andExpect(jsonPath("$.contenido[1].solicitud_asistencia.id").value(nullValue()))
                .andExpect(jsonPath("$.contenido[1].solicitud_asistencia.estado_envio").value("PENDIENTE_ENVIO"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total_elementos").value(2))
                .andExpect(jsonPath("$.total_paginas").value(1));
    }

    /**
     * Una solicitud RECHAZADA o pendiente de envío se muestra así, para que el coordinador la registre a mano
     *
     * @param estadoEnvio estado de envío de la solicitud
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @ValueSource(strings = { "PENDIENTE_ENVIO", "RECHAZADA" })
    void asistenciaMuestraLasSolicitudesQueNoLlegaronAMs05(String estadoEnvio) throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigentesConAsistencia(EVENTO, false, NADA, NADA, NADA, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(checkinAsistencia(ANA, TipoUbicacion.PUNTO_ENCUENTRO, PUNTO_A)),
                        PageRequest.of(0, 20), 1));
        when(solicitudes.findByEventoIdAndUsuarioIdIn(EVENTO, List.of(ANA)))
                .thenReturn(List.of(solicitud(ANA, null, EstadoEnvio.valueOf(estadoEnvio))));

        mockMvc.perform(como(BASE + "/asistencia", "COORDINADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].ubicacion.tipo").value("PUNTO_ENCUENTRO"))
                .andExpect(jsonPath("$.contenido[0].solicitud_asistencia.id").value(nullValue()))
                .andExpect(jsonPath("$.contenido[0].solicitud_asistencia.estado_envio").value(estadoEnvio));
    }

    /**
     * A1: si nadie requiere asistencia responde 200 con la lista vacía y los totales en 0
     *
     * @throws Exception si falla la petición
     */
    @Test
    void siNadieRequiereAsistenciaResponde200ConLaListaVacia() throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigentesConAsistencia(EVENTO, false, NADA, NADA, NADA, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(como(BASE + "/asistencia", "COORDINADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", empty()))
                .andExpect(jsonPath("$.total_elementos").value(0))
                .andExpect(jsonPath("$.total_paginas").value(0));
    }

    /**
     * Pide la página indicada y la pasa a la consulta
     *
     * @throws Exception si falla la petición
     */
    @Test
    void asistenciaRespetaLaPaginaYElTamanoPedidos() throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigentesConAsistencia(EVENTO, false, NADA, NADA, NADA, PageRequest.of(1, 1)))
                .thenReturn(new PageImpl<>(List.of(checkinAsistencia(LUIS, TipoUbicacion.ESPACIO, ESPACIO)),
                        PageRequest.of(1, 1), 3));
        when(solicitudes.findByEventoIdAndUsuarioIdIn(EVENTO, List.of(LUIS)))
                .thenReturn(List.of(solicitud(LUIS, "SOL-2026-0043", EstadoEnvio.ENVIADA)));

        mockMvc.perform(como(BASE + "/asistencia?page=1&size=1", "ORGANIZADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].usuario_id").value(LUIS.toString()))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total_elementos").value(3))
                .andExpect(jsonPath("$.total_paginas").value(3));
    }

    /**
     * Los roles con ms04:dashboard:read pueden consultar la lista de asistencia
     *
     * @param rol valor de la cabecera X-User-Role
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @ValueSource(strings = { "COORDINADOR", "ORGANIZADOR", "AUDITOR", "BRIGADISTA,COORDINADOR" })
    void losRolesConElPermisoPuedenConsultarAsistencia(String rol) throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigentesConAsistencia(EVENTO, false, NADA, NADA, NADA, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(como(BASE + "/asistencia", rol)).andExpect(status().isOk());

        verifyNoInteractions(zonasBrigadista);
    }

    /**
     * A2: el BRIGADISTA consulta con las zonas que MS05 le entrega y el id de usuario sale de la cabecera
     *
     * @throws Exception si falla la petición
     */
    @Test
    void asistenciaDelBrigadistaUsaSusZonasYSuIdentidadDeLasCabeceras() throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(zonasBrigadista.zonasDe(any())).thenReturn(new ZonasAsignadas(Set.of(EDIFICIO), Set.of(ESPACIO),
                Set.of(PUNTO_A)));
        when(checkins.buscarVigentesConAsistencia(EVENTO, true, Set.of(PUNTO_A), Set.of(EDIFICIO), Set.of(ESPACIO),
                PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(checkinAsistencia(ANA, TipoUbicacion.EDIFICIO, EDIFICIO)),
                        PageRequest.of(0, 20), 1));
        when(solicitudes.findByEventoIdAndUsuarioIdIn(EVENTO, List.of(ANA)))
                .thenReturn(List.of(solicitud(ANA, "SOL-2026-0042", EstadoEnvio.ENVIADA)));

        mockMvc.perform(como(BASE + "/asistencia", "BRIGADISTA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].usuario_id").value(ANA.toString()))
                .andExpect(jsonPath("$.total_elementos").value(1));

        verify(zonasBrigadista).zonasDe(new RequesterIdentity(UUID.fromString(USUARIO), EnumSet.of(Rol.BRIGADISTA)));
    }

    /**
     * Si MS05 no entrega las zonas del BRIGADISTA, la lista responde 503
     *
     * @throws Exception si falla la petición
     */
    @Test
    void asistenciaDelBrigadistaSinZonasDeMs05Responde503() throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(zonasBrigadista.zonasDe(any())).thenThrow(ApiException.dependenciaNoDisponible());

        mockMvc.perform(como(BASE + "/asistencia", "BRIGADISTA"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.codigo").value("DEPENDENCIA_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));

        verifyNoInteractions(checkins, solicitudes, nombres);
    }

    /**
     * A4: un rol sin ms04:dashboard:read recibe 403 sin consultar nada
     *
     * @param rol valor de la cabecera X-User-Role
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @ValueSource(strings = { "ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO", "ADMIN", "ESTUDIANTE,PROFESOR" })
    void losRolesSinElPermisoNoPuedenConsultarAsistencia(String rol) throws Exception {
        mockMvc.perform(como(BASE + "/asistencia", rol))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));

        verifyNoInteractions(eventos, checkins, solicitudes, nombres, zonasBrigadista);
    }

    /**
     * Sin cabeceras de identidad la lista responde 401
     *
     * @throws Exception si falla la petición
     */
    @Test
    void asistenciaSinCabecerasDeIdentidadEsNoAutenticado() throws Exception {
        mockMvc.perform(get(BASE + "/asistencia"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    /**
     * Una paginación inválida responde 400 con el mensaje del formulario F-03 y sin consultar nada
     *
     * @param consulta parámetros de paginación inválidos
     * @param campo parámetro que se reporta
     * @param mensaje mensaje del formulario F-03
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "page=-1|page|Página no válida.",
            "page=abc|page|Página no válida.",
            "size=0|size|Tamaño de página no válido (1 a 100).",
            "size=101|size|Tamaño de página no válido (1 a 100).",
            "size=abc|size|Tamaño de página no válido (1 a 100)." })
    void asistenciaConPaginacionInvalidaResponde400(String consulta, String campo, String mensaje)
            throws Exception {
        mockMvc.perform(como(BASE + "/asistencia?" + consulta, "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value(campo))
                .andExpect(jsonPath("$.errores[0].mensaje").value(mensaje));

        verifyNoInteractions(eventos, checkins, solicitudes, nombres);
    }

    /**
     * Un eventoId que no es UUID responde 400 con el mensaje de RG-03
     *
     * @throws Exception si falla la petición
     */
    @Test
    void asistenciaConUnEventoIdInvalidoResponde400() throws Exception {
        mockMvc.perform(como("/api/v1/chi/eventos/no-es-un-uuid/asistencia", "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("eventoId"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Evento no válido."));
    }

    /**
     * A5: un evento inexistente responde 404
     *
     * @throws Exception si falla la petición
     */
    @Test
    void asistenciaDeUnEventoInexistenteResponde404() throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(false);

        mockMvc.perform(como(BASE + "/asistencia", "COORDINADOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("Evento no encontrado."));

        verifyNoInteractions(checkins, solicitudes, nombres);
    }
}
