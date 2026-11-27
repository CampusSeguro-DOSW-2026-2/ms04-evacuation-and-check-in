package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.DatosUsuario;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.UsuarioNombresClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.DetalleCheckinMs06;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.DetalleCheckinsMs06;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;

/**
 * RF-MS04-12 Deliver to MS06 the detail of the check-ins of the event, with the repositories and the MS01 client
 * replaced by mocks: one element per person with the current status, zonaId only with SAFE, the name from MS01 and
 * the page format agreed with MS06
 */
@ExtendWith(MockitoExtension.class)
class DetalleCheckinsServiceTest {

    private static final UUID EVENT = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID ANA = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID LUIS = UUID.fromString("6c0d2f48-1b3e-4a57-9d8c-2e4f6a8b0c13");
    private static final UUID POINT = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID BUILDING = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");

    @Mock
    private EventoRepository events;

    @Mock
    private CheckinRepository checkins;

    @Mock
    private UsuarioNombresClient names;

    @InjectMocks
    private DetalleCheckinsService service;

    /**
     * Current check-in of a person as the view of current statuses returns it
     *
     * @param user user that reported
     * @param state current state
     * @param point meeting point, only with SAFE
     * @param registeredAt date of the report
     * @return the check-in
     */
    private static Checkin current(UUID user, EstadoCheckin state, UUID point, String registeredAt) {
        Checkin checkin;
        if (state == EstadoCheckin.NEEDS_ASSISTANCE) {
            checkin = new Checkin(EVENT, user, state, null, TipoUbicacion.EDIFICIO, BUILDING);
        } else {
            checkin = new Checkin(EVENT, user, state, point, null, null);
        }
        ReflectionTestUtils.setField(checkin, "registradoEn", Instant.parse(registeredAt));
        return checkin;
    }

    /**
     * Each person appears once with the current status, zonaId is the meeting point only with SAFE and the name
     * comes from MS01
     */
    @Test
    void eachPersonAppearsWithTheCurrentStatusAndItsName() {
        Checkin safe = current(ANA, EstadoCheckin.SAFE, POINT, "2026-11-20T15:02:05Z");
        Checkin assistance = current(LUIS, EstadoCheckin.NEEDS_ASSISTANCE, null, "2026-11-20T15:04:12Z");
        Page<Checkin> page = new PageImpl<>(List.of(safe, assistance), PageRequest.of(0, 20), 2);
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentesParaMs06(EVENT, null, PageRequest.of(0, 20))).thenReturn(page);
        when(names.nombresDe(List.of(ANA, LUIS))).thenReturn(Map.of(ANA, "Ana Gómez"));

        DetalleCheckinsMs06 detail = service.detalle(EVENT, null, 0, 20);

        assertEquals(0, detail.page());
        assertEquals(20, detail.size());
        assertEquals(2, detail.total());
        DetalleCheckinMs06 first = detail.items().get(0);
        assertEquals(ANA, first.usuarioId());
        assertEquals("Ana Gómez", first.nombre());
        assertEquals(EstadoCheckin.SAFE, first.estado());
        assertEquals(POINT, first.zonaId());
        assertEquals(Instant.parse("2026-11-20T15:02:05Z"), first.registradoEn());
        DetalleCheckinMs06 second = detail.items().get(1);
        assertNull(second.nombre());
        assertNull(second.zonaId());
    }

    /**
     * The state filter is sent to the query with its code and the total counts the people of the filter
     */
    @Test
    void theStateFilterIsSentWithItsCode() {
        Checkin safe = current(ANA, EstadoCheckin.SAFE, POINT, "2026-11-20T15:02:05Z");
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentesParaMs06(EVENT, "SAFE", PageRequest.of(1, 1)))
                .thenReturn(new PageImpl<>(List.of(safe), PageRequest.of(1, 1), 3));
        when(names.nombresDe(List.of(ANA))).thenReturn(Map.of());

        DetalleCheckinsMs06 detail = service.detalle(EVENT, EstadoCheckin.SAFE, 1, 1);

        assertEquals(3, detail.total());
        assertEquals(1, detail.items().size());
    }

    /**
     * An empty page does not call MS01
     */
    @Test
    void anEmptyPageDoesNotCallMs01() {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentesParaMs06(EVENT, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        DetalleCheckinsMs06 detail = service.detalle(EVENT, null, 0, 20);

        assertEquals(List.of(), detail.items());
        verifyNoInteractions(names);
    }

    /**
     * An event that MS04 does not know is not found
     */
    @Test
    void anUnknownEventIsNotFound() {
        when(events.existsById(EVENT)).thenReturn(false);

        ApiException error = assertThrows(ApiException.class, () -> service.detalle(EVENT, null, 0, 20));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
        verifyNoInteractions(checkins, names);
    }

    /**
     * The default method of the client keeps only the name of each person
     */
    @Test
    void theNamesComeFromTheDataOfMs01() {
        DatosUsuario ana = new DatosUsuario("Ana Gómez", "ana.gomez@mail.escuelaing.edu.co");
        UsuarioNombresClient client = ids -> Map.of(ANA, ana);

        assertEquals(Map.of(ANA, "Ana Gómez"), client.nombresDe(List.of(ANA)));
    }
}
