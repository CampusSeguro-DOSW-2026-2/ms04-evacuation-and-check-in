package edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

/**
 * Event and assistance request repositories without database: the default methods that build the parameters of the
 * native queries, and the locks that serialize the changes of an event, the check-ins and the sending of a request
 */
class EventoRepositoryTest {

    private static final UUID EVENT = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");

    private EventoRepository repository;

    /**
     * Creates a repository whose default methods run for real and whose queries are recorded
     */
    @BeforeEach
    void setUp() {
        repository = mock(EventoRepository.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
    }

    /**
     * Reads a method of the event repository
     *
     * @param name name of the method
     * @param parameterTypes types of its parameters
     * @return the method
     * @throws NoSuchMethodException if the method does not exist
     */
    private static Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return EventoRepository.class.getMethod(name, parameterTypes);
    }

    /**
     * The active events of a user are searched with the roles as a PostgreSQL array
     */
    @Test
    void theRolesAreSentAsAnArray() {
        repository.buscarActivosPorRoles(List.of("ESTUDIANTE", "PROFESOR"));

        verify(repository).buscarActivosPorRoles("{ESTUDIANTE,PROFESOR}");
    }

    /**
     * A user without roles has no active events and the database is not queried
     */
    @Test
    void withoutRolesThereAreNoActiveEvents() {
        assertEquals(List.of(), repository.buscarActivosPorRoles(List.of()));
        assertEquals(List.of(), repository.buscarActivosPorRoles((List<String>) null));

        verify(repository, never()).buscarActivosPorRoles(anyString());
    }

    /**
     * The calls of MS06 on the same event share the key of the advisory lock, also when the event does not exist
     */
    @Test
    void theCallsOnAnEventShareTheLockKey() {
        repository.bloquearEvento(EVENT);

        verify(repository).bloquear("evento:" + EVENT);
    }

    /**
     * A change of state reads the event with an exclusive lock, and a check-in with a shared lock, so a closing
     * waits for the check-in in progress
     *
     * @throws NoSuchMethodException if a method does not exist
     */
    @Test
    void theEventIsReadWithTheLockOfEachOperation() throws NoSuchMethodException {
        assertEquals(LockModeType.PESSIMISTIC_WRITE, method("buscarParaActualizar", UUID.class)
                .getAnnotation(Lock.class).value());
        assertEquals(LockModeType.PESSIMISTIC_READ, method("buscarParaCheckin", UUID.class)
                .getAnnotation(Lock.class).value());
        assertTrue(method("bloquear", String.class).getAnnotation(Query.class).value()
                .contains("pg_advisory_xact_lock"));
    }

    /**
     * The query of events orders by start date from the most recent with the drafts at the end, and only the events
     * in execution that convene one of the roles are active
     *
     * @throws NoSuchMethodException if a method does not exist
     */
    @Test
    void theQueriesOfEventsKeepTheirOrderAndFilter() throws NoSuchMethodException {
        assertTrue(method("consultar", String.class, Pageable.class).getAnnotation(Query.class).value()
                .contains("ORDER BY fecha_inicio DESC NULLS LAST, creado_en DESC, id"));
        String active = method("buscarActivosPorRoles", String.class).getAnnotation(Query.class).value();
        assertTrue(active.contains("estado = 'EN_EJECUCION'"));
        assertTrue(active.contains("roles_convocados && CAST(:roles AS VARCHAR(30)[])"));
    }

    /**
     * A pending request is taken with a row lock that skips the rows taken by another send, so it is never sent
     * twice at the same time
     *
     * @throws NoSuchMethodException if the method does not exist
     */
    @Test
    void aPendingRequestIsTakenSkippingLockedRows() throws NoSuchMethodException {
        String query = SolicitudAsistenciaRepository.class.getMethod("tomarPendiente", UUID.class)
                .getAnnotation(Query.class).value();

        assertTrue(query.contains("estado_envio = 'PENDIENTE_ENVIO'"));
        assertTrue(query.endsWith("FOR UPDATE SKIP LOCKED"));
    }
}
