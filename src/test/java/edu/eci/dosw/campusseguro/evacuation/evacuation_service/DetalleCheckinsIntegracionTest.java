package edu.eci.dosw.campusseguro.evacuation.evacuation_service;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.UsuarioNombresClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.PostgresTestcontainerConfig;
import jakarta.persistence.EntityManager;

/**
 * RF-MS04-12 Entregar a MS06 el detalle de check-ins del evento (historia KAIR-603), de punta a punta: la ruta
 * interna con X-Service-Name, la consulta a PostgreSQL real y el formato acordado con MS06. MS01 (los nombres) es un
 * doble. Cada prueba se deshace al terminar
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainerConfig.class)
@Transactional
class DetalleCheckinsIntegracionTest {

    private static final String BASE = "/api/v1/chi/eventos/";
    private static final UUID PUNTO = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID ANA = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID BETO = UUID.fromString("5a2b7c9d-1e3f-4a5b-8c7d-9e0f1a2b3c4d");
    private static final UUID CARLA = UUID.fromString("6b3c8d0e-2f4a-4b6c-9d8e-0f1a2b3c4d5e");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventoRepository eventos;

    @Autowired
    private CheckinRepository checkins;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private UsuarioNombresClient nombres;

    @BeforeEach
    void ms01EntregaLosNombres() {
        when(nombres.nombresDe(any())).thenReturn(Map.of(
                ANA, "Ana María Pérez Gómez", BETO, "Beto Ruiz", CARLA, "Carla Díaz"));
    }

    private Evento evento(EstadoEvento estado) {
        Evento evento = new Evento(UUID.randomUUID());
        evento.setNombre("Simulacro bloque A");
        evento.setSimulacro(true);
        evento.setEstado(estado);
        evento.setFechaInicio(estado == EstadoEvento.BORRADOR ? null : Instant.parse("2026-11-20T15:00:00Z"));
        evento.setFechaCierre(estado == EstadoEvento.FINALIZADO ? Instant.parse("2026-11-20T18:00:00Z") : null);
        evento.setRolesConvocados(List.of("ESTUDIANTE"));
        return eventos.saveAndFlush(evento);
    }

    private Checkin reportar(Evento evento, UUID usuario, EstadoCheckin estado) {
        boolean asistencia = estado == EstadoCheckin.NEEDS_ASSISTANCE;
        Checkin checkin = checkins.save(new Checkin(evento.getId(), usuario, estado,
                estado == EstadoCheckin.SAFE ? PUNTO : null, asistencia ? TipoUbicacion.EDIFICIO : null,
                asistencia ? UUID.randomUUID() : null));
        entityManager.flush();
        return checkin;
    }

    private ResultActions consultar(UUID eventoId, String query) throws Exception {
        return mockMvc.perform(get(BASE + eventoId + "/checkins" + query)
                .header(IdentityHeaders.SERVICE_NAME, "ms06"));
    }

    @Test
    void unaPersonaConVariosCheckinsApareceUnaVezConSuCheckinMasReciente() throws Exception {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        reportar(evento, ANA, EstadoCheckin.EVACUATING);
        reportar(evento, ANA, EstadoCheckin.NEEDS_ASSISTANCE);
        Checkin vigente = reportar(evento, ANA, EstadoCheckin.SAFE);

        consultar(evento.getId(), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].usuarioId").value(ANA.toString()))
                .andExpect(jsonPath("$.items[0].nombre").value("Ana María Pérez Gómez"))
                .andExpect(jsonPath("$.items[0].estado").value("SAFE"))
                .andExpect(jsonPath("$.items[0].zonaId").value(PUNTO.toString()))
                .andExpect(jsonPath("$.items[0].registradoEn").value(vigente.getRegistradoEn().toString()));
    }

    @Test
    void lasPersonasVanDelCheckinMasAntiguoAlMasRecienteYZonaIdSoloConSafe() throws Exception {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        reportar(evento, BETO, EstadoCheckin.OFF_CAMPUS);
        reportar(evento, ANA, EstadoCheckin.SAFE);
        reportar(evento, CARLA, EstadoCheckin.NEEDS_ASSISTANCE);

        consultar(evento.getId(), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].usuarioId").value(BETO.toString()))
                .andExpect(jsonPath("$.items[1].usuarioId").value(ANA.toString()))
                .andExpect(jsonPath("$.items[2].usuarioId").value(CARLA.toString()))
                .andExpect(jsonPath("$.items[0].zonaId").doesNotExist())
                .andExpect(jsonPath("$.items[1].zonaId").value(PUNTO.toString()))
                .andExpect(jsonPath("$.items[2].zonaId").doesNotExist());
    }

    @Test
    void elFiltroDeEstadoDevuelveSoloEseEstadoVigenteYTotalCuentaLasPersonasDelFiltro() throws Exception {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        reportar(evento, ANA, EstadoCheckin.SAFE);
        reportar(evento, BETO, EstadoCheckin.SAFE);
        reportar(evento, CARLA, EstadoCheckin.SAFE);
        // Carla ya no está a salvo: su estado vigente es otro y no debe aparecer
        reportar(evento, CARLA, EstadoCheckin.EVACUATING);

        consultar(evento.getId(), "?estado=SAFE&page=0&size=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].usuarioId").value(ANA.toString()));
        consultar(evento.getId(), "?estado=SAFE&page=1&size=1")
                .andExpect(jsonPath("$.items[0].usuarioId").value(BETO.toString()));
        consultar(evento.getId(), "?estado=EVACUATING")
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].usuarioId").value(CARLA.toString()));
    }

    @Test
    void sinCheckinsResponde200ConTotal0EItemsVacioSinLlamarAMs01() throws Exception {
        Evento evento = evento(EstadoEvento.PROGRAMADO);

        consultar(evento.getId(), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));
        verify(nombres, never()).nombresDe(any());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoEvento.class, names = { "PROGRAMADO", "EN_EJECUCION", "FINALIZADO" })
    void funcionaEnCualquierEstadoDelEvento(EstadoEvento estado) throws Exception {
        Evento evento = evento(estado);
        if (estado != EstadoEvento.PROGRAMADO) {
            reportar(evento, ANA, EstadoCheckin.OFF_CAMPUS);
        }

        consultar(evento.getId(), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(estado == EstadoEvento.PROGRAMADO ? 0 : 1));
    }

    @Test
    void conMs01DetenidoResponde200ConNombreNulo() throws Exception {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        reportar(evento, ANA, EstadoCheckin.EVACUATING);
        // El cliente de MS01 no lanza: si MS01 no responde entrega un mapa vacío y deja el fallo en el log
        when(nombres.nombresDe(any())).thenReturn(Map.of());

        consultar(evento.getId(), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].usuarioId").value(ANA.toString()))
                .andExpect(jsonPath("$.items[0].nombre").doesNotExist())
                .andExpect(jsonPath("$.items[0].estado").value("EVACUATING"));
    }

    @Test
    void soloSePidenAMs01LosNombresDeLaPagina() throws Exception {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        reportar(evento, ANA, EstadoCheckin.EVACUATING);
        reportar(evento, BETO, EstadoCheckin.EVACUATING);

        consultar(evento.getId(), "?size=1");

        verify(nombres).nombresDe(List.of(ANA));
    }

    @Test
    void unEventoInexistenteEs404() throws Exception {
        consultar(UUID.randomUUID(), "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("Evento no encontrado."));
    }

    @Test
    void losParametrosInvalidosSon400ConLosMensajesDeF06() throws Exception {
        UUID eventoId = evento(EstadoEvento.EN_EJECUCION).getId();

        consultar(eventoId, "?estado=HERIDO")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("estado"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Estado no válido."));
        consultar(eventoId, "?page=-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Página no válida."));
        consultar(eventoId, "?size=101")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Tamaño de página no válido (1 a 100)."));
    }

    @Test
    void sinServicioEs401ConOtroServicioEs403YUnUsuarioNoPuedeUsarla() throws Exception {
        UUID eventoId = evento(EstadoEvento.EN_EJECUCION).getId();

        mockMvc.perform(get(BASE + eventoId + "/checkins"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mockMvc.perform(get(BASE + eventoId + "/checkins").header(IdentityHeaders.SERVICE_NAME, "ms03"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        mockMvc.perform(get(BASE + eventoId + "/checkins")
                .header(IdentityHeaders.USER_ID, ANA.toString()).header(IdentityHeaders.USER_ROLE, "COORDINADOR"))
                .andExpect(status().isUnauthorized());
    }
}
