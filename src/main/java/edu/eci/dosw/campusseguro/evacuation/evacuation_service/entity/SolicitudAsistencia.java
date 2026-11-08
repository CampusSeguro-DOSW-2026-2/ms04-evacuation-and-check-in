package edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Solicitud de asistencia generada desde un check-in Requiero asistencia y su estado de envío a MS05
 * (RF-MS04-04). Una por usuario y evento (RN-MS04-11). Su id viaja a MS05 como llave de idempotencia (RN-MS04-12)
 */
@Entity
@Table(name = "solicitudes_asistencia")
public class SolicitudAsistencia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "checkin_id", nullable = false, updatable = false)
    private UUID checkinId;

    @Column(name = "evento_id", nullable = false, updatable = false)
    private UUID eventoId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ubicacion_tipo", nullable = false, length = 20, updatable = false)
    private TipoUbicacion ubicacionTipo;

    @Column(name = "ubicacion_id", nullable = false, updatable = false)
    private UUID ubicacionId;

    @Column(name = "solicitud_ms05_id", length = 64)
    private String solicitudMs05Id;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_envio", nullable = false, length = 16)
    private EstadoEnvio estadoEnvio = EstadoEnvio.PENDIENTE_ENVIO;

    @Column(name = "intentos", nullable = false)
    private int intentos;

    @Column(name = "ultimo_intento_en")
    private Instant ultimoIntentoEn;

    @Column(name = "reportada_en", nullable = false, updatable = false)
    private Instant reportadaEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /**
     * Constructor para JPA
     */
    protected SolicitudAsistencia() {
    }

    /**
     * Crea una solicitud pendiente de envío a partir del check-in que la originó
     *
     * @param checkinId check-in NEEDS_ASSISTANCE que originó la solicitud
     * @param eventoId evento del check-in
     * @param usuarioId usuario del check-in
     * @param ubicacionTipo tipo de ubicación reportada
     * @param ubicacionId id de MS02 de la ubicación
     * @param reportadaEn fecha/hora del check-in que originó la solicitud
     */
    public SolicitudAsistencia(UUID checkinId, UUID eventoId, UUID usuarioId, TipoUbicacion ubicacionTipo,
            UUID ubicacionId, Instant reportadaEn) {
        this.checkinId = checkinId;
        this.eventoId = eventoId;
        this.usuarioId = usuarioId;
        this.ubicacionTipo = ubicacionTipo;
        this.ubicacionId = ubicacionId;
        this.reportadaEn = reportadaEn;
    }

    /**
     * Completa las fechas antes de insertar
     */
    @PrePersist
    void alCrear() {
        Instant ahora = Instant.now();
        if (reportadaEn == null) {
            reportadaEn = ahora;
        }
        actualizadoEn = ahora;
    }

    /**
     * Actualiza la fecha del último cambio
     */
    @PreUpdate
    void alActualizar() {
        actualizadoEn = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCheckinId() {
        return checkinId;
    }

    public UUID getEventoId() {
        return eventoId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public TipoUbicacion getUbicacionTipo() {
        return ubicacionTipo;
    }

    public UUID getUbicacionId() {
        return ubicacionId;
    }

    public String getSolicitudMs05Id() {
        return solicitudMs05Id;
    }

    public void setSolicitudMs05Id(String solicitudMs05Id) {
        this.solicitudMs05Id = solicitudMs05Id;
    }

    public EstadoEnvio getEstadoEnvio() {
        return estadoEnvio;
    }

    public void setEstadoEnvio(EstadoEnvio estadoEnvio) {
        this.estadoEnvio = estadoEnvio;
    }

    public int getIntentos() {
        return intentos;
    }

    public void setIntentos(int intentos) {
        this.intentos = intentos;
    }

    public Instant getUltimoIntentoEn() {
        return ultimoIntentoEn;
    }

    public void setUltimoIntentoEn(Instant ultimoIntentoEn) {
        this.ultimoIntentoEn = ultimoIntentoEn;
    }

    public Instant getReportadaEn() {
        return reportadaEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
