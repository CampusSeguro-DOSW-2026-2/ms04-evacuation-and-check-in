package edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.PostgresTestcontainerConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

/**
 * Restricciones del modelo de datos (historia KAIR-586, subtarea KAIR-619), probadas con SQL directo para
 * demostrar que la base de datos las garantiza aunque la aplicación no las validara: los cuatro estados del
 * check-in, el punto de encuentro solo con SAFE, las fechas coherentes con el estado del evento, que un evento que
 * no es simulacro nunca esté en BORRADOR ni PROGRAMADO y que los check-ins solo se agreguen
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainerConfig.class)
class RestriccionesBaseDatosTest {

    private static final String USUARIO = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";
    private static final String PUNTO = "7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35";

    @Autowired
    private EntityManager entityManager;

    private final String evento = UUID.randomUUID().toString();

    private void ejecutar(String sql) {
        entityManager.createNativeQuery(sql).executeUpdate();
        entityManager.flush();
    }

    private String insertarEvento(String id, boolean simulacro, String estado, String fechaInicio,
            String fechaCierre) {
        return "INSERT INTO eventos (id, nombre, simulacro, estado, fecha_inicio, fecha_cierre, roles_convocados) "
                + "VALUES ('" + id + "', 'Simulacro', " + simulacro + ", '" + estado + "', "
                + (fechaInicio == null ? "NULL" : "'" + fechaInicio + "'") + ", "
                + (fechaCierre == null ? "NULL" : "'" + fechaCierre + "'") + ", ARRAY['ESTUDIANTE']::VARCHAR(20)[])";
    }

    private String insertarCheckin(String estado, String punto) {
        return "INSERT INTO checkins (evento_id, usuario_id, estado, punto_encuentro_id) VALUES ('" + evento
                + "', '" + USUARIO + "', '" + estado + "', " + (punto == null ? "NULL" : "'" + punto + "'") + ")";
    }

    @BeforeEach
    void crearEventoEnEjecucion() {
        ejecutar(insertarEvento(evento, true, "EN_EJECUCION", "2026-11-20T15:00:00Z", null));
    }

    @Test
    void unUsuarioPuedeTenerVariosCheckinsYLaVistaDevuelveSoloElMasReciente() {
        ejecutar(insertarCheckin("EVACUATING", null));
        ejecutar(insertarCheckin("OFF_CAMPUS", null));
        ejecutar(insertarCheckin("SAFE", PUNTO));

        Object vigentes = entityManager.createNativeQuery("SELECT COUNT(*) FROM v_checkins_vigentes WHERE "
                + "evento_id = '" + evento + "'").getSingleResult();
        Object estado = entityManager.createNativeQuery("SELECT estado FROM v_checkins_vigentes WHERE "
                + "evento_id = '" + evento + "'").getSingleResult();
        assertEquals(1L, ((Number) vigentes).longValue());
        assertEquals("SAFE", estado);
    }

    @Test
    void unEstadoDeCheckinFueraDeLosCuatroFallaPorCheck() {
        assertThrows(PersistenceException.class, () -> ejecutar(insertarCheckin("HERIDO", null)));
    }

    @Test
    void elPuntoDeEncuentroVaSoloYSiempreConSafe() {
        assertThrows(PersistenceException.class, () -> ejecutar(insertarCheckin("SAFE", null)));
    }

    @Test
    void unEstadoDistintoDeSafeNoLlevaPuntoDeEncuentro() {
        assertThrows(PersistenceException.class, () -> ejecutar(insertarCheckin("EVACUATING", PUNTO)));
    }

    @Test
    void unProgramadoSinFechaDeInicioFallaPorCheck() {
        assertThrows(PersistenceException.class,
                () -> ejecutar(insertarEvento(UUID.randomUUID().toString(), true, "PROGRAMADO", null, null)));
    }

    @Test
    void unBorradorConFechaDeInicioFallaPorCheck() {
        assertThrows(PersistenceException.class, () -> ejecutar(
                insertarEvento(UUID.randomUUID().toString(), true, "BORRADOR", "2026-11-20T15:00:00Z", null)));
    }

    @Test
    void unFinalizadoSinFechaDeCierreFallaPorCheck() {
        assertThrows(PersistenceException.class, () -> ejecutar(
                insertarEvento(UUID.randomUUID().toString(), true, "FINALIZADO", "2026-11-20T15:00:00Z", null)));
    }

    @Test
    void unaFechaDeCierreSinEstarFinalizadoFallaPorCheck() {
        assertThrows(PersistenceException.class, () -> ejecutar(insertarEvento(UUID.randomUUID().toString(), true,
                "EN_EJECUCION", "2026-11-20T15:00:00Z", "2026-11-20T18:00:00Z")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "BORRADOR", "PROGRAMADO" })
    void unEventoRealPuedeEstarEnBorradorOProgramado(String estado) {
        // Sigue los estados que envía MS06, igual que un simulacro (RN-MS04-20, database/004)
        String fecha = estado.equals("BORRADOR") ? null : "2026-11-20T15:00:00Z";
        assertDoesNotThrow(() -> ejecutar(insertarEvento(UUID.randomUUID().toString(), false, estado, fecha, null)));
    }

    @Test
    void unRolConvocadoFueraDelCatalogoFallaPorCheck() {
        assertThrows(PersistenceException.class, () -> ejecutar("INSERT INTO eventos (id, nombre, simulacro, "
                + "estado, fecha_inicio, roles_convocados) VALUES ('" + UUID.randomUUID() + "', 'x', true, "
                + "'PROGRAMADO', now(), ARRAY['ADMIN']::VARCHAR(20)[])"));
    }

    @Test
    void unEventoCoherenteSeGuarda() {
        assertDoesNotThrow(() -> ejecutar(insertarEvento(UUID.randomUUID().toString(), true, "FINALIZADO",
                "2026-11-20T15:00:00Z", "2026-11-20T18:00:00Z")));
    }

    @Test
    void losCheckinsNoSePuedenEliminar() {
        ejecutar(insertarCheckin("EVACUATING", null));

        assertThrows(PersistenceException.class,
                () -> ejecutar("DELETE FROM checkins WHERE evento_id = '" + evento + "'"));
    }

    @Test
    void losCheckinsNoSePuedenVaciarConTruncate() {
        assertThrows(PersistenceException.class, () -> ejecutar("TRUNCATE checkins CASCADE"));
    }
}
