package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Periodic task that resends the pending assistance requests to MS05 (RN-MS04-12): it delegates to the service and
 * never stops the scheduler with an error
 */
@ExtendWith(MockitoExtension.class)
class ReenvioSolicitudesTareaTest {

    @Mock
    private SolicitudAsistenciaService requests;

    @InjectMocks
    private ReenvioSolicitudesTarea task;

    /**
     * Each execution resends the pending requests
     */
    @Test
    void eachExecutionResendsThePendingRequests() {
        when(requests.reenviarPendientes()).thenReturn(2);

        task.reenviar();

        verify(requests).reenviarPendientes();
    }

    /**
     * An execution without sent requests ends without error
     */
    @Test
    void anExecutionWithoutSentRequestsEndsWithoutError() {
        when(requests.reenviarPendientes()).thenReturn(0);

        assertDoesNotThrow(() -> task.reenviar());
    }

    /**
     * An error is left in the log and the next execution tries again
     */
    @Test
    void anErrorDoesNotStopTheTask() {
        when(requests.reenviarPendientes()).thenThrow(new IllegalStateException("database"));

        assertDoesNotThrow(() -> task.reenviar());
    }
}
