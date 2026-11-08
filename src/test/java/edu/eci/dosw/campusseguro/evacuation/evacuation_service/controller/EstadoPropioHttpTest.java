package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.config.SecurityConfig;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.config.WebMvcConfig;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEnvio;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.SolicitudAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.SolicitudAsistenciaRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.CorrelationIdFilter;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.EstadoPropioService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;

/**
 * Contrato HTTP de RF-MS04-05: GET /api/v1/chi/eventos/{eventoId}/checkins/propio con el service real y los
 * repositorios simulados. Comprueba la forma de la respuesta, los errores estándar y que la identidad sale solo de
 * las cabeceras
 */
@WebMvcTest(controllers = EstadoPropioController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, GlobalExceptionHandler.class, CorrelationIdFilter.class,
        EstadoPropioService.class })
@TestPropertySource(properties = "internal.allowed-services=ms06")
class EstadoPropioHttpTest {

    private static final String USUARIO = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";
    private static final String OTRO_USUARIO = "5d2e8f10-6a4b-4c93-b7e1-3f9a0c2d4e68";
    private static final UUID USUARIO_ID = UUID.fromString(USUARIO);
    private static final UUID EVENTO = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID PUNTO = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID EDIFICIO = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
    private static final UUID CHECKIN = UUID.fromString("9a1b2c3d-4e5f-4a6b-8c7d-0e1f2a3b4c5d");
    private static final Instant REGISTRADO = Instant.parse("2026-11-20T15:04:12Z");
    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
    private static final String RUTA = "/api/v1/chi/eventos/" + EVENTO + "/checkins/propio";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventoRepository eventos;

    @MockitoBean
    private CheckinRepository checkins;

    @MockitoBean
    private SolicitudAsistenciaRepository solicitudes;

    /**
     * Crea un check-in como lo devuelve la base de datos, con el id y la fecha que asigna ella
     *
     * @param estado estado reportado
     * @param puntoEncuentro punto de encuentro, solo con SAFE
     * @param tipo tipo de ubicación, solo con NEEDS_ASSISTANCE
     * @param ubicacion id de la ubicación, solo con NEEDS_ASSISTANCE
     * @return el check-in con su id y su fecha de registro
     */
    private Checkin checkin(EstadoCheckin estado, UUID puntoEncuentro, TipoUbicacion tipo, UUID ubicacion) {
        Checkin checkin = new Checkin(EVENTO, USUARIO_ID, estado, puntoEncuentro, tipo, ubicacion);
        ReflectionTestUtils.setField(checkin, "id", CHECKIN);
        ReflectionTestUtils.setField(checkin, "registradoEn", REGISTRADO);
        return checkin;
    }

    /**
     * Petición de un usuario con el rol indicado
     *
     * @param rol rol o roles de la cabecera X-User-Role
     * @return la petición con las cabeceras de identidad y de correlación
     */
    private MockHttpServletRequestBuilder como(String rol) {
        return get(RUTA).header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, rol)
                .header(CorrelationIds.HEADER, CORRELATION);
    }

    /**
     * Con SAFE responde 200 con los campos del contrato en snake_case
     *
     * @throws Exception si falla la petición
     */
    @Test
    void conSafeResponde200ConElContratoEnSnakeCase() throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigente(EVENTO, USUARIO_ID))
                .thenReturn(Optional.of(checkin(EstadoCheckin.SAFE, PUNTO, null, null)));

        mockMvc.perform(como("ESTUDIANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CHECKIN.toString()))
                .andExpect(jsonPath("$.evento_id").value(EVENTO.toString()))
                .andExpect(jsonPath("$.estado").value("SAFE"))
                .andExpect(jsonPath("$.punto_encuentro_id").value(PUNTO.toString()))
                .andExpect(jsonPath("$.ubicacion").value(nullValue()))
                .andExpect(jsonPath("$.solicitud_asistencia").value(nullValue()))
                .andExpect(jsonPath("$.fecha_hora").value("2026-11-20T15:04:12Z"));
    }

    /**
     * Con NEEDS_ASSISTANCE responde la ubicación y el estado de envío de la solicitud
     *
     * @throws Exception si falla la petición
     */
    @Test
    void conNeedsAssistanceTraeLaUbicacionYElEstadoDeLaSolicitud() throws Exception {
        SolicitudAsistencia pendiente = new SolicitudAsistencia(CHECKIN, EVENTO, USUARIO_ID, TipoUbicacion.EDIFICIO,
                EDIFICIO, REGISTRADO);
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigente(EVENTO, USUARIO_ID)).thenReturn(Optional
                .of(checkin(EstadoCheckin.NEEDS_ASSISTANCE, null, TipoUbicacion.EDIFICIO, EDIFICIO)));
        when(solicitudes.findByEventoIdAndUsuarioId(EVENTO, USUARIO_ID)).thenReturn(Optional.of(pendiente));

        mockMvc.perform(como("PROFESOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("NEEDS_ASSISTANCE"))
                .andExpect(jsonPath("$.punto_encuentro_id").value(nullValue()))
                .andExpect(jsonPath("$.ubicacion.tipo").value("EDIFICIO"))
                .andExpect(jsonPath("$.ubicacion.id").value(EDIFICIO.toString()))
                .andExpect(jsonPath("$.solicitud_asistencia.id").value(nullValue()))
                .andExpect(jsonPath("$.solicitud_asistencia.estado_envio")
                        .value(EstadoEnvio.PENDIENTE_ENVIO.name()));
    }

    /**
     * Los seis roles de la comunidad y la unión de roles pueden consultar su estado
     *
     * @param rol valor de la cabecera X-User-Role
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @ValueSource(strings = { "ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO", "BRIGADISTA", "COORDINADOR", "ORGANIZADOR",
            "ESTUDIANTE,BRIGADISTA" })
    void losRolesDeLaComunidadPuedenConsultarSuEstado(String rol) throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigente(EVENTO, USUARIO_ID))
                .thenReturn(Optional.of(checkin(EstadoCheckin.EVACUATING, null, null, null)));

        mockMvc.perform(como(rol)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EVACUATING"));
    }

    /**
     * Los roles sin ms04:checkin:read_own reciben 403 y no se consulta la base
     *
     * @param rol valor de la cabecera X-User-Role
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @ValueSource(strings = { "AUDITOR", "ADMIN" })
    void losRolesSinElPermisoSonProhibidosYNoLlegaAlaBase(String rol) throws Exception {
        mockMvc.perform(como(rol))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"))
                .andExpect(jsonPath("$.errores", hasSize(0)))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));

        verifyNoInteractions(eventos, checkins, solicitudes);
    }

    /**
     * Sin cabeceras de identidad responde 401 NO_AUTENTICADO
     *
     * @throws Exception si falla la petición
     */
    @Test
    void sinCabecerasDeIdentidadEsNoAutenticado() throws Exception {
        mockMvc.perform(get(RUTA).header(CorrelationIds.HEADER, CORRELATION))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(CorrelationIds.HEADER, CORRELATION))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaders.MISSING_MESSAGE))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));

        verifyNoInteractions(eventos, checkins, solicitudes);
    }

    /**
     * Sin check-in responde 404 con el ErrorResponse de cinco campos
     *
     * @throws Exception si falla la petición
     */
    @Test
    void sinCheckinResponde404ConElErrorEstandar() throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigente(EVENTO, USUARIO_ID)).thenReturn(Optional.empty());

        mockMvc.perform(como("ESTUDIANTE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("No tiene check-in en este evento."))
                .andExpect(jsonPath("$.errores", hasSize(0)))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));
    }

    /**
     * Un evento inexistente responde 404 y no consulta check-ins
     *
     * @throws Exception si falla la petición
     */
    @Test
    void unEventoInexistenteResponde404() throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(false);

        mockMvc.perform(como("ESTUDIANTE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("Evento no encontrado."));

        verifyNoInteractions(checkins, solicitudes);
    }

    /**
     * Un eventoId que no es UUID responde 400 VALIDACION
     *
     * @throws Exception si falla la petición
     */
    @Test
    void unEventoIdQueNoEsUuidEsValidacion() throws Exception {
        mockMvc.perform(get("/api/v1/chi/eventos/no-es-uuid/checkins/propio")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "ESTUDIANTE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("eventoId"));

        verifyNoInteractions(eventos, checkins, solicitudes);
    }

    /**
     * Un id de usuario en la consulta se ignora: la identidad sale solo de las cabeceras
     *
     * @throws Exception si falla la petición
     */
    @Test
    void laIdentidadSaleSoloDeLasCabecerasNoDeLaConsulta() throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigente(EVENTO, USUARIO_ID))
                .thenReturn(Optional.of(checkin(EstadoCheckin.OFF_CAMPUS, null, null, null)));

        mockMvc.perform(como("ESTUDIANTE").queryParam("usuario_id", OTRO_USUARIO).queryParam("userId", OTRO_USUARIO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("OFF_CAMPUS"));

        verify(checkins).buscarVigente(EVENTO, USUARIO_ID);
        verify(checkins, never()).buscarVigente(EVENTO, UUID.fromString(OTRO_USUARIO));
    }

    /**
     * Consultar no agrega ni modifica check-ins ni solicitudes
     *
     * @throws Exception si falla la petición
     */
    @Test
    void consultarNoAgregaNiModificaCheckins() throws Exception {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigente(EVENTO, USUARIO_ID))
                .thenReturn(Optional.of(checkin(EstadoCheckin.SAFE, PUNTO, null, null)));

        mockMvc.perform(como("ESTUDIANTE")).andExpect(status().isOk());

        verify(checkins, never()).save(any());
        verify(solicitudes, never()).save(any());
    }
}
