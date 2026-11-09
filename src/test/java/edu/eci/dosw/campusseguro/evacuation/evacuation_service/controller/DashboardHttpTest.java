package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

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

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.UsuarioNombresClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ZonasBrigadistaClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ZonasBrigadistaClient.ZonasAsignadas;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.config.SecurityConfig;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.config.WebMvcConfig;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository.ConteoPorEstadoYPunto;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.CorrelationIdFilter;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Rol;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.DashboardService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;

/**
 * Contrato HTTP de RF-MS04-06: GET /api/v1/chi/eventos/{eventoId}/consolidado y /personas con el service real y los
 * repositorios y clientes de MS01 y MS05 simulados. Comprueba la forma de las respuestas, los errores estándar, el
 * permiso ms04:dashboard:read y que la identidad sale solo de las cabeceras
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
    private UsuarioNombresClient nombres;

    @MockitoBean
    private ZonasBrigadistaClient zonasBrigadista;

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
}
