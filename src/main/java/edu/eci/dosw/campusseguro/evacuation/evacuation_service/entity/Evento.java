package edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Evento (simulacro o emergencia) que MS06 le informa a MS04 (RF-MS04-11). El id es el eventoId que asigna MS06:
 * MS04 no lo genera. Solo la recepción del estado del evento crea o modifica esta entidad
 */
@Entity
@Table(name = "eventos")
public class Evento {

    @Id
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "simulacro", nullable = false)
    private boolean simulacro;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoEvento estado;

    @Column(name = "fecha_inicio")
    private Instant fechaInicio;

    @Column(name = "fecha_cierre")
    private Instant fechaCierre;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "roles_convocados", nullable = false)
    private List<String> rolesConvocados = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "escenario_edificios", nullable = false)
    private List<String> escenarioEdificios = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "escenario_zonas", nullable = false)
    private List<String> escenarioZonas = new ArrayList<>();

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /**
     * Constructor para JPA
     */
    protected Evento() {
    }

    /**
     * Crea un evento con el id que asignó MS06
     *
     * @param id eventoId de MS06
     */
    public Evento(UUID id) {
        this.id = id;
    }

    /**
     * Fija las fechas de auditoría del registro antes de insertarlo
     */
    @PrePersist
    void alCrear() {
        Instant ahora = Instant.now();
        creadoEn = ahora;
        actualizadoEn = ahora;
    }

    /**
     * Actualiza la fecha del último cambio recibido de MS06
     */
    @PreUpdate
    void alActualizar() {
        actualizadoEn = Instant.now();
    }

    /**
     * Indica si el check-in del evento está habilitado (RN-MS04-19)
     *
     * @return true solo si el evento está EN_EJECUCION
     */
    public boolean isCheckinHabilitado() {
        return estado == EstadoEvento.EN_EJECUCION;
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public boolean isSimulacro() {
        return simulacro;
    }

    public void setSimulacro(boolean simulacro) {
        this.simulacro = simulacro;
    }

    public EstadoEvento getEstado() {
        return estado;
    }

    public void setEstado(EstadoEvento estado) {
        this.estado = estado;
    }

    public Instant getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(Instant fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public Instant getFechaCierre() {
        return fechaCierre;
    }

    public void setFechaCierre(Instant fechaCierre) {
        this.fechaCierre = fechaCierre;
    }

    public List<String> getRolesConvocados() {
        return rolesConvocados;
    }

    public void setRolesConvocados(List<String> rolesConvocados) {
        this.rolesConvocados = rolesConvocados == null ? new ArrayList<>() : new ArrayList<>(rolesConvocados);
    }

    public List<String> getEscenarioEdificios() {
        return escenarioEdificios;
    }

    public void setEscenarioEdificios(List<String> escenarioEdificios) {
        this.escenarioEdificios = escenarioEdificios == null ? new ArrayList<>() : new ArrayList<>(escenarioEdificios);
    }

    public List<String> getEscenarioZonas() {
        return escenarioZonas;
    }

    public void setEscenarioZonas(List<String> escenarioZonas) {
        this.escenarioZonas = escenarioZonas == null ? new ArrayList<>() : new ArrayList<>(escenarioZonas);
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
