package edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.validation.FieldError;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ErrorResponse.ErrorDetail;

/**
 * Convierte los errores de Bean Validation en el detalle por campo del {@code ErrorResponse} (RG-06). El nombre
 * del campo es el del contrato (p. ej. {@code punto_encuentro_id}) y no el del atributo de Java
 */
public final class ValidationErrors {

    public static final String INVALID_DATA_MESSAGE = ErrorCode.VALIDATION.getMessage();
    public static final String INVALID_VALUE_MESSAGE = "Valor no válido.";
    public static final String UNKNOWN_FIELD_MESSAGE = "El campo no está permitido.";

    private ValidationErrors() {
    }

    /**
     * Nombre con el que el campo aparece en el contrato JSON
     *
     * @param type clase del cuerpo de la petición
     * @param field nombre del atributo en Java
     * @return el valor de {@code @JsonProperty} del atributo si lo tiene; si no, el mismo nombre
     */
    public static String jsonName(Class<?> type, String field) {
        try {
            Field declared = type.getDeclaredField(field);
            JsonProperty property = declared.getAnnotation(JsonProperty.class);
            if (property != null && !property.value().isBlank()) {
                return property.value();
            }
        } catch (NoSuchFieldException e) {
            // un atributo anidado o heredado conserva su nombre
        }
        return field;
    }

    /**
     * Errores de campo de un cuerpo que no pasó la validación
     *
     * @param type clase del cuerpo
     * @param fieldErrors errores de Bean Validation
     * @return un error por campo, sin repetir el mismo campo
     */
    public static List<ErrorDetail> fromFieldErrors(Class<?> type, List<FieldError> fieldErrors) {
        Map<String, ErrorDetail> byField = new LinkedHashMap<>();
        for (FieldError error : fieldErrors) {
            String name = jsonName(type, error.getField());
            String message = error.getDefaultMessage() == null ? INVALID_VALUE_MESSAGE : error.getDefaultMessage();
            byField.putIfAbsent(name, new ErrorDetail(name, message));
        }
        return new ArrayList<>(byField.values());
    }
}
