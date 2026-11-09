package edu.eci.dosw.campusseguro.evacuation.evacuation_service.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL real para las pruebas, con el mismo esquema que en producción: los scripts de database/ se copian
 * a /docker-entrypoint-initdb.d y el contenedor los ejecuta al arrancar. Necesita Docker
 *
 * Spring reutiliza el contenedor entre las clases de prueba que comparten la misma configuración
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainerConfig {

    /** Misma versión mayor que el docker-compose del proyecto */
    public static final String IMAGEN = "postgres:17-alpine";

    /**
     * Contenedor de PostgreSQL con el esquema de MS04
     *
     * @return el contenedor; Spring toma de él la URL, el usuario y la contraseña
     */
    @Bean
    @ServiceConnection
    @SuppressWarnings("resource")
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(IMAGEN)
                .withCopyFileToContainer(MountableFile.forHostPath("database"), "/docker-entrypoint-initdb.d/");
    }
}
