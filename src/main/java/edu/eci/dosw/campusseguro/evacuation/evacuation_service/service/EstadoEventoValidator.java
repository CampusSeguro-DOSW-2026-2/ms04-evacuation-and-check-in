package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ErrorResponse.ErrorDetail;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Escenario;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EstadoEventoRequest;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;

/**
 * Valida el cuerpo que envía MS06 con el estado del evento (F-05, RN-MS04-24) y lo deja normalizado. Los campos y
 * los mensajes son los de F-05. Reúne todos los errores en una sola respuesta 400
 */
public final class EstadoEventoValidator {

    public static final String CAMPO_EVENTO_ID = "eventoId";
    public static final String CAMPO_NOMBRE = "nombre";
    public static final String CAMPO_SIMULACRO = "simulacro";
    public static final String CAMPO_ESTADO = "estado";
    public static final String CAMPO_FECHA_INICIO = "fechaInicio";
    public static final String CAMPO_EDIFICIOS = "escenario.edificios";
    public static final String CAMPO_ZONAS = "escenario.zonas";
    public static final String CAMPO_ROLES = "rolesConvocados";

    public static final String EVENTO_NO_VALIDO = "Evento no válido.";
    public static final String EVENTO_DISTINTO_DE_LA_RUTA = "El id del evento del cuerpo no coincide con la ruta.";
    public static final String NOMBRE_NO_VALIDO = "El nombre del evento es obligatorio (1 a 150 caracteres).";
    public static final String FALTA_SIMULACRO = "Indique si el evento es un simulacro.";
    public static final String ESTADO_NO_VALIDO = "Estado de evento no válido.";
    public static final String FALTA_FECHA = "La fecha de inicio es obligatoria para programar el evento.";
    public static final String FECHA_EN_BORRADOR = "Un evento en borrador no lleva fecha de inicio.";
    public static final String FECHA_NO_VALIDA = "Fecha de inicio no válida (ISO-8601 con desfase horario).";
    public static final String EDIFICIO_NO_VALIDO = "Edificio no válido (1 a 50 caracteres).";
    public static final String ZONA_NO_VALIDA = "Zona no válida (1 a 50 caracteres).";
    public static final String FALTAN_ROLES = "Indique al menos un rol convocado.";
    public static final String ROL_NO_VALIDO = "Rol convocado no válido.";

    static final int MAX_NOMBRE = 150;
    static final int MAX_ESCENARIO = 50;

    /** Roles que pueden reportar su estado y por eso pueden convocarse (RN-MS04-24) */
    static final Set<String> ROLES_CONVOCABLES = Set.of("ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO", "BRIGADISTA",
            "COORDINADOR", "ORGANIZADOR");

    private EstadoEventoValidator() {
    }

    /**
     * Cuerpo ya validado y normalizado
     *
     * @param nombre nombre sin espacios laterales (RG-01)
     * @param simulacro tipo de evento
     * @param estado estado recibido (no el efectivo)
     * @param fechaInicio fecha de inicio en UTC; null si no llegó
     * @param edificios edificios del escenario, sin espacios laterales
     * @param zonas zonas del escenario, sin espacios laterales
     * @param roles roles convocados, en el orden recibido
     */
    public record EstadoValidado(String nombre, boolean simulacro, EstadoEvento estado, Instant fechaInicio,
            List<String> edificios, List<String> zonas, List<String> roles) {
    }

    /**
     * Valida el cuerpo contra F-05
     *
     * @param eventoIdRuta id del evento de la ruta
     * @param request cuerpo recibido
     * @return los datos normalizados
     * @throws ApiException 400 VALIDACION con un error por cada campo inválido
     */
    public static EstadoValidado validar(UUID eventoIdRuta, EstadoEventoRequest request) {
        if (request == null) {
            throw ApiException.validacion(CAMPO_EVENTO_ID, EVENTO_NO_VALIDO);
        }
        List<ErrorDetail> errores = new ArrayList<>();

        if (request.eventoId() == null) {
            errores.add(new ErrorDetail(CAMPO_EVENTO_ID, EVENTO_NO_VALIDO));
        } else if (!request.eventoId().equals(eventoIdRuta)) {
            errores.add(new ErrorDetail(CAMPO_EVENTO_ID, EVENTO_DISTINTO_DE_LA_RUTA));
        }

        String nombre = request.nombre() == null ? "" : request.nombre().trim();
        if (nombre.isEmpty() || nombre.length() > MAX_NOMBRE) {
            errores.add(new ErrorDetail(CAMPO_NOMBRE, NOMBRE_NO_VALIDO));
        }

        if (request.simulacro() == null) {
            errores.add(new ErrorDetail(CAMPO_SIMULACRO, FALTA_SIMULACRO));
        }

        if (request.estado() == null) {
            errores.add(new ErrorDetail(CAMPO_ESTADO, ESTADO_NO_VALIDO));
        }

        Instant fechaInicio = validarFecha(request, errores);

        Escenario escenario = request.escenario() == null ? new Escenario(List.of(), List.of()) : request.escenario();
        List<String> edificios = textosDelEscenario(escenario.edificios(), CAMPO_EDIFICIOS, EDIFICIO_NO_VALIDO,
                errores);
        List<String> zonas = textosDelEscenario(escenario.zonas(), CAMPO_ZONAS, ZONA_NO_VALIDA, errores);

        List<String> roles = request.rolesConvocados();
        if (roles == null || roles.isEmpty()) {
            errores.add(new ErrorDetail(CAMPO_ROLES, FALTAN_ROLES));
        } else if (!rolesValidos(roles)) {
            errores.add(new ErrorDetail(CAMPO_ROLES, ROL_NO_VALIDO));
        }

        if (!errores.isEmpty()) {
            throw ApiException.validacion(errores);
        }
        return new EstadoValidado(nombre, request.simulacro(), request.estado(), fechaInicio, edificios, zonas,
                List.copyOf(roles));
    }

    /**
     * La fecha llega en ISO-8601 con desfase horario y se guarda en UTC. En un simulacro es obligatoria con
     * PROGRAMADO y no se envía con BORRADOR (RN-MS04-24). Un evento que no es simulacro queda EN_EJECUCION sea cual
     * sea el estado enviado y, sin fecha, toma la de recepción (RN-MS04-20), así que esas dos reglas no le aplican.
     * No se compara con el reloj
     */
    private static Instant validarFecha(EstadoEventoRequest request, List<ErrorDetail> errores) {
        String texto = request.fechaInicio();
        boolean noEsSimulacro = Boolean.FALSE.equals(request.simulacro());
        if (texto == null || texto.isBlank()) {
            if (request.estado() == EstadoEvento.PROGRAMADO && !noEsSimulacro) {
                errores.add(new ErrorDetail(CAMPO_FECHA_INICIO, FALTA_FECHA));
            }
            return null;
        }
        if (request.estado() == EstadoEvento.BORRADOR && !noEsSimulacro) {
            errores.add(new ErrorDetail(CAMPO_FECHA_INICIO, FECHA_EN_BORRADOR));
            return null;
        }
        try {
            return OffsetDateTime.parse(texto.trim(), DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (DateTimeParseException e) {
            errores.add(new ErrorDetail(CAMPO_FECHA_INICIO, FECHA_NO_VALIDA));
            return null;
        }
    }

    private static List<String> textosDelEscenario(List<String> textos, String campo, String mensaje,
            List<ErrorDetail> errores) {
        if (textos == null) {
            return List.of();
        }
        List<String> normalizados = new ArrayList<>();
        for (String texto : textos) {
            String limpio = texto == null ? "" : texto.trim();
            if (limpio.isEmpty() || limpio.length() > MAX_ESCENARIO) {
                errores.add(new ErrorDetail(campo, mensaje));
                return List.of();
            }
            normalizados.add(limpio);
        }
        return List.copyOf(normalizados);
    }

    /** Solo roles de la comunidad y sin repetidos (RN-MS04-24) */
    private static boolean rolesValidos(List<String> roles) {
        Set<String> vistos = new HashSet<>();
        for (String rol : roles) {
            if (rol == null || !ROLES_CONVOCABLES.contains(rol) || !vistos.add(rol)) {
                return false;
            }
        }
        return true;
    }
}
