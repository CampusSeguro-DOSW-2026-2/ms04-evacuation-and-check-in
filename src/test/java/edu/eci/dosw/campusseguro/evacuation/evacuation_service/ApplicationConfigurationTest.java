package edu.eci.dosw.campusseguro.evacuation.evacuation_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * Base configuration of MS04 required by the integration standards: the port of the module, the sensitive values
 * taken from environment variables, the schema controlled by the scripts, the strict reading of the bodies, only the
 * health exposed, Swagger off by default and the defaults of the outgoing calls and of the periodic resend
 */
class ApplicationConfigurationTest {

    private static Properties properties;

    /**
     * Loads the application.yaml used by the service
     */
    @BeforeAll
    static void load() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yaml"));
        properties = factory.getObject();
        assertNotNull(properties);
    }

    /**
     * The service listens on the internal port assigned to MS04
     */
    @Test
    void theServiceListensOnThePortOfTheModule() {
        assertEquals("${SERVER_PORT:8084}", properties.getProperty("server.port"));
    }

    /**
     * The connection to the database comes only from environment variables, without default values
     */
    @Test
    void theDatabaseComesFromEnvironmentVariables() {
        assertEquals("${DB_URL}", properties.getProperty("spring.datasource.url"));
        assertEquals("${DB_USERNAME}", properties.getProperty("spring.datasource.username"));
        assertEquals("${DB_PASSWORD}", properties.getProperty("spring.datasource.password"));
    }

    /**
     * The schema is created by the scripts and Hibernate only validates it, with dates in UTC
     */
    @Test
    void jpaOnlyValidatesTheSchema() {
        assertEquals("validate", properties.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("false", properties.getProperty("spring.jpa.open-in-view"));
        assertEquals("UTC", properties.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone"));
    }

    /**
     * A field that the contract does not define is rejected
     */
    @Test
    void unknownFieldsAreRejected() {
        assertEquals("true", properties.getProperty("spring.jackson.deserialization.fail-on-unknown-properties"));
    }

    /**
     * Only the health is exposed, without details, components nor probes, and there is no whitelabel error page
     */
    @Test
    void onlyTheHealthIsExposedWithoutDetails() {
        assertEquals("health", properties.getProperty("management.endpoints.web.exposure.include"));
        assertEquals("never", properties.getProperty("management.endpoint.health.show-details"));
        assertEquals("never", properties.getProperty("management.endpoint.health.show-components"));
        assertEquals("false", properties.getProperty("management.endpoint.health.probes.enabled"));
        assertEquals("false", properties.getProperty("server.error.whitelabel.enabled"));
    }

    /**
     * The API documentation is turned off unless it is enabled for local development
     */
    @Test
    void swaggerIsOffByDefault() {
        assertEquals("${SWAGGER_ENABLED:false}", properties.getProperty("springdoc.api-docs.enabled"));
        assertEquals("${SWAGGER_ENABLED:false}", properties.getProperty("springdoc.swagger-ui.enabled"));
    }

    /**
     * The outgoing calls go through the Gateway with the service key, both from environment variables, and the
     * client timeout of the standards
     */
    @Test
    void theOutgoingCallsGoThroughTheGateway() {
        assertEquals("${GATEWAY_URL:}", properties.getProperty("gateway.url"));
        assertEquals("${GATEWAY_SERVICE_KEY:}", properties.getProperty("gateway.service-key"));
        assertEquals("${GATEWAY_TIMEOUT_MS:2000}", properties.getProperty("gateway.timeout-ms"));
    }

    /**
     * Only MS06 uses the internal routes by default, the pending requests are resent every 30 seconds and a request
     * slower than 5 seconds is logged
     */
    @Test
    void theDefaultsOfTheInternalRoutesAndTasks() {
        assertEquals("${INTERNAL_ALLOWED_SERVICES:ms06}", properties.getProperty("internal.allowed-services"));
        assertEquals("${MS04_REENVIO_SOLICITUDES_MS:30000}",
                properties.getProperty("solicitudes-asistencia.reenvio-ms"));
        assertEquals("${RENDIMIENTO_UMBRAL_LENTO_MS:5000}", properties.getProperty("rendimiento.umbral-lento-ms"));
    }
}
