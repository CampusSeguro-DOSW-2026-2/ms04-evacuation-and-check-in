package edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEnvio;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.SolicitudAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import jakarta.persistence.Column;
import jakarta.persistence.Table;

/**
 * Coherence between the schema scripts of database/, applied in order on a new database, and the entities that
 * Hibernate validates when MS04 starts (ddl-auto validate). It also checks the rules that the database guarantees
 * even if the application did not validate them: the catalogs of states, the meeting point only with SAFE, the
 * location only with NEEDS_ASSISTANCE, the dates coherent with the state of the event, the format of the convened
 * roles, one request per user and event, the current status view and the append only check-ins
 */
class SchemaScriptsTest {

    private static final Path SCRIPTS = Path.of("database");

    private static String schema;

    /**
     * Reads every script of database/ in the order in which they are applied, without the comments
     *
     * @throws IOException if a script cannot be read
     */
    @BeforeAll
    static void readScripts() throws IOException {
        List<Path> scripts;
        try (Stream<Path> files = Files.list(SCRIPTS)) {
            scripts = files.filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        }
        StringBuilder all = new StringBuilder();
        for (Path script : scripts) {
            for (String line : Files.readAllLines(script, StandardCharsets.UTF_8)) {
                if (!line.trim().startsWith("--")) {
                    all.append(line).append('\n');
                }
            }
        }
        schema = all.toString();
    }

    /**
     * Definition of a table: its CREATE TABLE and every later ALTER TABLE on it
     *
     * @param table name of the table
     * @return the SQL that defines the table
     */
    private static String table(String table) {
        StringBuilder definition = new StringBuilder();
        int start = schema.indexOf("CREATE TABLE " + table + " (");
        assertTrue(start >= 0, "missing table " + table);
        definition.append(schema, start, schema.indexOf(");", start) + 2);
        Matcher alters = Pattern.compile("ALTER TABLE " + table + "\\b[^;]*;").matcher(schema);
        while (alters.find()) {
            definition.append('\n').append(alters.group());
        }
        return definition.toString();
    }

    /**
     * Name of the table of an entity
     *
     * @param entity entity class
     * @return the name of its table
     */
    private static String tableOf(Class<?> entity) {
        return entity.getAnnotation(Table.class).name();
    }

    /**
     * Mapped columns of an entity with their declared length
     *
     * @param entity entity class
     * @return the column annotations of its fields
     */
    private static List<Column> columnsOf(Class<?> entity) {
        List<Column> columns = new ArrayList<>();
        for (Field field : entity.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if (column != null) {
                columns.add(column);
            }
        }
        return columns;
    }

    /**
     * Every column mapped by the entities exists in its table, and every limited text column has the length of the
     * entity, so Hibernate validates the schema when MS04 starts
     */
    @Test
    void everyMappedColumnExistsWithItsLength() {
        for (Class<?> entity : List.of(Evento.class, Checkin.class, SolicitudAsistencia.class)) {
            String definition = table(tableOf(entity));
            for (Column column : columnsOf(entity)) {
                assertTrue(definition.contains(column.name() + " "),
                        "missing column " + column.name() + " in " + tableOf(entity));
                if (column.length() != 255) {
                    assertTrue(definition.contains(column.name() + " VARCHAR(" + column.length() + ")"),
                            "length of " + column.name() + " in " + tableOf(entity));
                }
            }
        }
    }

    /**
     * The checks of the states accept exactly the values of the enums of the application
     */
    @Test
    void theCatalogsMatchTheEnums() {
        for (EstadoCheckin state : EstadoCheckin.values()) {
            assertTrue(table("checkins").contains("'" + state.name() + "'"));
        }
        for (EstadoEvento state : EstadoEvento.values()) {
            assertTrue(table("eventos").contains("'" + state.name() + "'"));
        }
        for (EstadoEnvio state : EstadoEnvio.values()) {
            assertTrue(table("solicitudes_asistencia").contains("'" + state.name() + "'"));
        }
        for (TipoUbicacion type : TipoUbicacion.values()) {
            assertTrue(table("checkins").contains("'" + type.name() + "'"));
            assertTrue(table("solicitudes_asistencia").contains("'" + type.name() + "'"));
        }
    }

    /**
     * The meeting point goes only and always with SAFE, and the location only and always with NEEDS_ASSISTANCE
     */
    @Test
    void thePlaceOfTheCheckinDependsOnTheState() {
        String checkins = table("checkins");

        assertTrue(checkins.contains("CHECK ((punto_encuentro_id IS NOT NULL) = (estado = 'SAFE'))"));
        assertTrue(checkins.contains("CHECK ((ubicacion_tipo IS NOT NULL) = (estado = 'NEEDS_ASSISTANCE')"));
        assertTrue(checkins.contains("AND (ubicacion_id IS NOT NULL) = (ubicacion_tipo IS NOT NULL))"));
    }

    /**
     * Only a draft lacks the start date, only a finished event has the closing date, and real events follow the
     * states of MS06 like the drills
     */
    @Test
    void theDatesAreCoherentWithTheStateOfTheEvent() {
        String events = table("eventos");

        assertTrue(events.contains("CHECK ((fecha_inicio IS NULL) = (estado = 'BORRADOR'))"));
        assertTrue(events.contains("CHECK ((fecha_cierre IS NOT NULL) = (estado = 'FINALIZADO'))"));
        assertTrue(events.contains("DROP CONSTRAINT IF EXISTS chk_eventos_simulacro"));
    }

    /**
     * The convened roles are at least one, without nulls and with the role format of the validator, in columns of up
     * to 30 characters; the scenario keeps names of MS02 of up to 100 characters
     */
    @Test
    void theRolesAndTheScenarioHaveTheFormatOfTheValidator() {
        String events = table("eventos");

        assertTrue(events.contains("ALTER COLUMN roles_convocados TYPE VARCHAR(30)[]"));
        assertTrue(events.contains("cardinality(roles_convocados) >= 1"));
        assertTrue(events.contains("array_position(roles_convocados, NULL) IS NULL"));
        assertTrue(events.contains("'^[A-Z][A-Z0-9_]{1,29}(,[A-Z][A-Z0-9_]{1,29})*$'"));
        assertTrue(events.contains("ALTER COLUMN escenario_edificios TYPE VARCHAR(100)[]"));
        assertTrue(events.contains("ALTER COLUMN escenario_zonas TYPE VARCHAR(100)[]"));
    }

    /**
     * A user has at most one assistance request per event, a sent request has the id of MS05 and the requests keep
     * the identity of the user for the periodic resend
     */
    @Test
    void theRequestsKeepTheirRules() {
        String requests = table("solicitudes_asistencia");

        assertTrue(requests.contains("UNIQUE (evento_id, usuario_id)"));
        assertTrue(requests.contains("CHECK (estado_envio <> 'ENVIADA' OR solicitud_ms05_id IS NOT NULL)"));
        assertTrue(requests.contains("CHECK (intentos >= 0)"));
        assertTrue(requests.contains("roles_solicitante TEXT"));
        assertTrue(requests.contains("permisos_solicitante TEXT"));
    }

    /**
     * The current status view keeps only the latest check-in of each person in each event
     */
    @Test
    void theViewKeepsTheLatestCheckinOfEachPerson() {
        assertTrue(schema.contains("CREATE VIEW v_checkins_vigentes AS"));
        assertTrue(schema.contains("SELECT DISTINCT ON (evento_id, usuario_id) *"));
        assertTrue(schema.contains("ORDER BY evento_id, usuario_id, registrado_en DESC, numero DESC;"));
    }

    /**
     * The check-ins are only appended: a trigger rejects every update, delete and truncate
     */
    @Test
    void theCheckinsAreOnlyAppended() {
        assertTrue(schema.contains("BEFORE UPDATE OR DELETE ON checkins"));
        assertTrue(schema.contains("BEFORE TRUNCATE ON checkins"));
        assertTrue(schema.contains("EXECUTE FUNCTION fn_checkins_solo_insercion()"));
    }

    /**
     * The data of an event cannot be deleted while it has check-ins or requests
     *
     * @param reference foreign key that restricts the delete
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "REFERENCES eventos (id) ON DELETE RESTRICT",
            "REFERENCES checkins (id) ON DELETE RESTRICT" })
    void theReferencesRestrictTheDelete(String reference) {
        assertTrue(schema.contains(reference));
    }

    /**
     * The database assigns the number and the date of a check-in, and the application never sends them
     */
    @Test
    void theDatabaseAssignsTheNumberAndTheDateOfACheckin() {
        String checkins = table("checkins");

        assertTrue(checkins.contains("numero BIGINT GENERATED ALWAYS AS IDENTITY"));
        assertTrue(checkins.contains("registrado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()"));
        for (Column column : columnsOf(Checkin.class)) {
            if (column.name().equals("numero") || column.name().equals("registrado_en")) {
                assertFalse(column.insertable());
                assertFalse(column.updatable());
            }
        }
    }
}
