package edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;

/**
 * Check-in repository without database: its default methods turn the filters and the MS02 ids into the parameters of
 * the native queries, and its declarations keep the append only rule, the current status taken from the view
 * v_checkins_vigentes and the chronological order of the history
 */
class CheckinRepositoryTest {

    private static final UUID EVENT = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID USER = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID POINT_A = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID POINT_B = UUID.fromString("9c3e5a1d-2b4f-4c6e-8a1b-3d5f7e9a1c24");
    private static final UUID BUILDING = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
    private static final UUID SPACE = UUID.fromString("5e2a8c4d-6f1b-4d3e-9c7a-1b3d5f7a9c46");
    private static final Pageable PAGE = PageRequest.of(0, 20);

    private CheckinRepository repository;

    /**
     * Creates a repository whose default methods run for real and whose queries are recorded
     */
    @BeforeEach
    void setUp() {
        repository = mock(CheckinRepository.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
    }

    /**
     * Reads the native query declared on a method of the repository
     *
     * @param name name of the method
     * @param parameterTypes types of its parameters
     * @return the SQL of the query
     * @throws NoSuchMethodException if the method does not exist
     */
    private static String queryOf(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return CheckinRepository.class.getMethod(name, parameterTypes).getAnnotation(Query.class).value();
    }

    /**
     * The count of a brigadier sends the ids of the assigned zones as PostgreSQL arrays, and without zones the
     * arrays are empty
     */
    @Test
    void theCountSendsTheZonesAsArrays() {
        repository.contarVigentes(EVENT, true, List.of(POINT_A, POINT_B), List.of(BUILDING), List.of());

        verify(repository).contarVigentes(EVENT, true, "{" + POINT_A + "," + POINT_B + "}", "{" + BUILDING + "}",
                "{}");
    }

    /**
     * The list of people sends the state and the meeting point as text, or null when there is no filter
     */
    @Test
    void theListOfPeopleSendsTheFiltersAsText() {
        repository.buscarVigentes(EVENT, EstadoCheckin.SAFE, POINT_A, false, List.of(), List.of(), List.of(), PAGE);
        repository.buscarVigentes(EVENT, null, null, true, List.of(POINT_A), List.of(), List.of(SPACE), PAGE);

        verify(repository).buscarVigentes(EVENT, "SAFE", POINT_A.toString(), false, "{}", "{}", "{}", PAGE);
        verify(repository).buscarVigentes(EVENT, (String) null, (String) null, true, "{" + POINT_A + "}", "{}",
                "{" + SPACE + "}", PAGE);
    }

    /**
     * The list of people who need assistance sends the zones as arrays
     */
    @Test
    void theAssistanceListSendsTheZonesAsArrays() {
        repository.buscarVigentesConAsistencia(EVENT, true, List.of(POINT_A), List.of(BUILDING), List.of(SPACE), PAGE);

        verify(repository).buscarVigentesConAsistencia(EVENT, true, "{" + POINT_A + "}", "{" + BUILDING + "}",
                "{" + SPACE + "}", PAGE);
    }

    /**
     * The history sends the user and the state as text, or null when there is no filter
     */
    @Test
    void theHistorySendsTheFiltersAsText() {
        repository.buscarHistorial(EVENT, USER, EstadoCheckin.NEEDS_ASSISTANCE, PAGE);
        repository.buscarHistorial(EVENT, (UUID) null, (EstadoCheckin) null, PAGE);

        verify(repository).buscarHistorial(EVENT, USER.toString(), "NEEDS_ASSISTANCE", PAGE);
        verify(repository).buscarHistorial(EVENT, (String) null, (String) null, PAGE);
    }

    /**
     * The reports of the same user in the same event share the key of the advisory lock
     */
    @Test
    void theReportsOfAUserShareTheLockKey() {
        repository.bloquearUsuarioEnEvento(EVENT, USER);

        verify(repository).bloquear("checkin:" + EVENT + ":" + USER);
    }

    /**
     * The check-ins are only appended: the repository has no update nor delete operation
     */
    @Test
    void theRepositoryHasNoUpdateNorDeleteOperation() {
        assertFalse(CrudRepository.class.isAssignableFrom(CheckinRepository.class));
        assertTrue(Repository.class.isAssignableFrom(CheckinRepository.class));
        boolean removes = Arrays.stream(CheckinRepository.class.getMethods())
                .map(method -> method.getName().toLowerCase())
                .anyMatch(name -> name.startsWith("delete") || name.startsWith("update") || name.startsWith("remove"));
        assertFalse(removes);
    }

    /**
     * The current status, the count and the lists of people come from the view that keeps only the latest check-in
     * of each person
     *
     * @throws NoSuchMethodException if a method does not exist
     */
    @Test
    void theCurrentStatusComesFromTheView() throws NoSuchMethodException {
        assertTrue(queryOf("buscarVigente", UUID.class, UUID.class).contains("FROM v_checkins_vigentes"));
        assertTrue(queryOf("contarVigentes", UUID.class, boolean.class, String.class, String.class, String.class)
                .contains("FROM v_checkins_vigentes"));
        assertTrue(queryOf("buscarVigentes", UUID.class, String.class, String.class, boolean.class, String.class,
                String.class, String.class, Pageable.class).contains("ORDER BY registrado_en DESC, numero DESC"));
        assertTrue(queryOf("buscarVigentesParaMs06", UUID.class, String.class, Pageable.class)
                .contains("FROM v_checkins_vigentes"));
    }

    /**
     * The history reads every check-in of the table, from the oldest to the most recent
     *
     * @throws NoSuchMethodException if the method does not exist
     */
    @Test
    void theHistoryReadsEveryCheckinInChronologicalOrder() throws NoSuchMethodException {
        String query = queryOf("buscarHistorial", UUID.class, String.class, String.class, Pageable.class);

        assertTrue(query.startsWith("SELECT * FROM checkins WHERE"));
        assertTrue(query.endsWith("ORDER BY registrado_en, numero"));
    }

    /**
     * The zones of a brigadier only show SAFE at one of their meeting points and NEEDS_ASSISTANCE at one of their
     * places; EVACUATING and OFF_CAMPUS never pass the filter
     */
    @Test
    void theZoneFilterOnlyShowsSafeAndNeedsAssistance() {
        String filter = CheckinRepository.FILTRO_ZONAS;

        assertTrue(filter.contains("estado = 'SAFE' AND punto_encuentro_id = ANY"));
        assertTrue(filter.contains("estado = 'NEEDS_ASSISTANCE'"));
        assertTrue(filter.contains("ubicacion_tipo = 'EDIFICIO'"));
        assertTrue(filter.contains("ubicacion_tipo = 'ESPACIO'"));
        assertTrue(filter.contains("ubicacion_tipo = 'PUNTO_ENCUENTRO'"));
        assertFalse(filter.contains("EVACUATING"));
        assertFalse(filter.contains("OFF_CAMPUS"));
    }

    /**
     * The lock of a user in an event is a transactional advisory lock of PostgreSQL
     *
     * @throws NoSuchMethodException if the method does not exist
     */
    @Test
    void theLockIsATransactionalAdvisoryLock() throws NoSuchMethodException {
        assertTrue(queryOf("bloquear", String.class).contains("pg_advisory_xact_lock"));
    }
}
