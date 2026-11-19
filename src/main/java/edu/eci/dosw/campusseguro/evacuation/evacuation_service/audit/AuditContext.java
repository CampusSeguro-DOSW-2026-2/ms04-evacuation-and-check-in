package edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;

/**
 * Evento de MS06 al que pertenece la acción y atributos para los indicadores de MS08 (Estándares, sección 12):
 * {@code evento_id}, {@code tipo_evento} y {@code atributos}. El tipo es obligatorio cuando hay evento, así que
 * los dos van juntos o ninguno
 *
 * @param eventoId evento al que pertenece la acción; null si no se conoce (p. ej. el evento no existe)
 * @param simulacro true si es simulacro, false si es emergencia real; null exactamente cuando no hay evento
 * @param atributos objeto plano con claves snake_case y valores de texto, número o booleano (solo ids, códigos y
 *        conteos); vacío si no hay
 */
public record AuditContext(UUID eventoId, Boolean simulacro, Map<String, Object> atributos) {

    /** Acción sin evento conocido ni atributos */
    public static final AuditContext NINGUNO = new AuditContext(null, null, Map.of());

    /**
     * Exige evento y tipo juntos y deja los atributos inmutables
     */
    public AuditContext {
        if ((eventoId == null) != (simulacro == null)) {
            throw new IllegalArgumentException("evento_id y tipo_evento van juntos");
        }
        atributos = atributos == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(atributos));
    }

    /**
     * Contexto de una acción dentro de un evento, sin atributos
     *
     * @param evento evento de MS04
     * @return el contexto con su id y su tipo
     */
    public static AuditContext de(Evento evento) {
        return new AuditContext(evento.getId(), evento.isSimulacro(), Map.of());
    }

    /**
     * Contexto con un atributo más; un valor null no se agrega
     *
     * @param clave clave en snake_case
     * @param valor texto, número, booleano o un valor que se envía como texto (enum, UUID)
     * @return un contexto nuevo con el atributo
     */
    public AuditContext con(String clave, Object valor) {
        if (valor == null) {
            return this;
        }
        Map<String, Object> nuevos = new LinkedHashMap<>(atributos);
        nuevos.put(clave, valor instanceof Number || valor instanceof Boolean ? valor : valor.toString());
        return new AuditContext(eventoId, simulacro, nuevos);
    }

    /**
     * @return SIMULACRO, REAL o null si no hay evento
     */
    public String tipoEvento() {
        if (simulacro == null) {
            return null;
        }
        return simulacro ? "SIMULACRO" : "REAL";
    }
}
