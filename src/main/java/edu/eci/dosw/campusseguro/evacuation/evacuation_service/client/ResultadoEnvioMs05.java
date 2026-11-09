package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

/**
 * Resultado de enviar una solicitud de asistencia a MS05 (RF-MS04-04)
 *
 * @param tipo cómo terminó el envío
 * @param solicitudMs05Id id que devolvió MS05; solo con {@link Tipo#CREADA}
 * @param detalle motivo legible para el log cuando no se creó; nunca es el cuerpo de error de MS05
 */
public record ResultadoEnvioMs05(Tipo tipo, String solicitudMs05Id, String detalle) {

    /**
     * Cómo terminó el envío
     */
    public enum Tipo {
        /** MS05 creó la solicitud (o ya la tenía con esa llave de idempotencia) y devolvió su id: queda ENVIADA */
        CREADA,
        /** MS05 rechazó la solicitud por datos inválidos (400 o 422): queda RECHAZADA y no se reenvía (A4) */
        RECHAZADA,
        /** MS05 no respondió, no respondió a tiempo o falló: queda PENDIENTE_ENVIO y se reenvía (A2) */
        NO_DISPONIBLE
    }

    public static ResultadoEnvioMs05 creada(String solicitudMs05Id) {
        return new ResultadoEnvioMs05(Tipo.CREADA, solicitudMs05Id, null);
    }

    public static ResultadoEnvioMs05 rechazada(String detalle) {
        return new ResultadoEnvioMs05(Tipo.RECHAZADA, null, detalle);
    }

    public static ResultadoEnvioMs05 noDisponible(String detalle) {
        return new ResultadoEnvioMs05(Tipo.NO_DISPONIBLE, null, detalle);
    }
}
