package edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un estado reportado por un usuario en un evento. Los check-ins solo se agregan: nunca se modifican ni se
 * eliminan (RN-MS04-07, RN-MS04-08). El estado vigente de un usuario es su check-in más reciente, que entrega la
 * vista v_checkins_vigentes
 *
 * {@code numero} y {@code registrado_en} los asigna la base de datos al insertar. Todas las columnas son
 * {@code updatable = false}, el repositorio no ofrece actualizar ni eliminar y un trigger de la base rechaza
 * cualquier UPDATE o DELETE. No se usa {@code @Immutable} porque impide que Hibernate relea esos dos valores
 * después del INSERT
 */
@Entity
@Table(name = "checkins")
public class Checkin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Generated(event = EventType.INSERT)
    @Column(name = "numero", insertable = false, updatable = false)
    private Long numero;

    @Column(name = "evento_id", nullable = false, updatable = false)
    private UUID eventoId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20, updatable = false)
    private EstadoCheckin estado;

    @Column(name = "punto_encuentro_id", updatable = false)
    private UUID puntoEncuentroId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ubicacion_tipo", length = 20, updatable = false)
    private TipoUbicacion ubicacionTipo;

    @Column(name = "ubicacion_id", updatable = false)
    private UUID ubicacionId;

    @Generated(event = EventType.INSERT)
    @Column(name = "registrado_en", insertable = false, updatable = false)
    private Instant registradoEn;

    /**
     * Constructor para JPA
     */
    protected Checkin() {
    }

    /**
     * Crea un check-in nuevo, todavía sin guardar
     *
     * @param eventoId evento al que pertenece el reporte
     * @param usuarioId usuario que reporta (X-User-Id)
     * @param estado estado reportado
     * @param puntoEncuentroId punto de encuentro de MS02, solo con SAFE
     * @param ubicacionTipo tipo de ubicación, solo con NEEDS_ASSISTANCE
     * @param ubicacionId id de MS02 de la ubicación, solo con NEEDS_ASSISTANCE
     */
    public Checkin(UUID eventoId, UUID usuarioId, EstadoCheckin estado, UUID puntoEncuentroId,
            TipoUbicacion ubicacionTipo, UUID ubicacionId) {
        this.eventoId = eventoId;
        this.usuarioId = usuarioId;
        this.estado = estado;
        this.puntoEncuentroId = puntoEncuentroId;
        this.ubicacionTipo = ubicacionTipo;
        this.ubicacionId = ubicacionId;
    }

    public UUID getId() {
        return id;
    }

    public Long getNumero() {
        return numero;
    }

    public UUID getEventoId() {
        return eventoId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public EstadoCheckin getEstado() {
        return estado;
    }

    public UUID getPuntoEncuentroId() {
        return puntoEncuentroId;
    }

    public TipoUbicacion getUbicacionTipo() {
        return ubicacionTipo;
    }

    public UUID getUbicacionId() {
        return ubicacionId;
    }

    public Instant getRegistradoEn() {
        return registradoEn;
    }
}
