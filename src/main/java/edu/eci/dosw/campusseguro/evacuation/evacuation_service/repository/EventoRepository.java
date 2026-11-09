package edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import jakarta.persistence.LockModeType;

/**
 * Acceso a los eventos que MS06 le informa a MS04
 */
public interface EventoRepository extends JpaRepository<Evento, UUID> {

    /**
     * Lee el evento con bloqueo exclusivo (SELECT ... FOR UPDATE), para validar y aplicar un cambio de estado sin
     * que otro cambio o un check-in se crucen (RF-MS04-11, RNF-MS04-18). Debe llamarse dentro de una transacción
     *
     * @param id eventoId
     * @return el evento bloqueado, si existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Evento e where e.id = :id")
    Optional<Evento> buscarParaActualizar(@Param("id") UUID id);

    /**
     * Toma un bloqueo asesor transaccional sobre la clave; se libera al terminar la transacción
     *
     * @param clave clave del bloqueo
     * @return siempre 1
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:clave, 0))) AS bloqueo",
            nativeQuery = true)
    Integer bloquear(@Param("clave") String clave);

    /**
     * Serializa las llamadas de MS06 sobre un mismo evento, también cuando el evento todavía no existe y no hay
     * fila que bloquear con FOR UPDATE: dos llamadas simultáneas se atienden una tras otra y ninguna falla por la
     * llave primaria (RNF-MS04-18). Debe llamarse al inicio de la transacción
     *
     * @param id eventoId
     */
    default void bloquearEvento(UUID id) {
        bloquear("evento:" + id);
    }

    /**
     * Lee el evento con bloqueo compartido (SELECT ... FOR SHARE) en la transacción del check-in: así un
     * FINALIZADO espera a que termine el check-in en curso y ninguno se cuela después (3_modelo-datos.md)
     *
     * @param id eventoId
     * @return el evento bloqueado para lectura, si existe
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select e from Evento e where e.id = :id")
    Optional<Evento> buscarParaCheckin(@Param("id") UUID id);

    /** Filtro opcional por estado de la consulta de eventos */
    String FILTRO_CONSULTA = "(CAST(:estado AS VARCHAR(12)) IS NULL OR estado = CAST(:estado AS VARCHAR(12))) ";

    /**
     * Eventos en cualquier estado, con filtro opcional por estado (RF-MS04-13): por fecha de inicio del más
     * reciente al más antiguo, con los BORRADOR (sin fecha) al final, y luego por {@code creado_en} descendente. El
     * id desempata para que la paginación sea estable
     *
     * @param estado código del estado a filtrar, null para todos
     * @param pageable página pedida, sin orden: el orden lo fija la consulta
     * @return la página de eventos
     */
    @Query(value = "SELECT * FROM eventos WHERE " + FILTRO_CONSULTA
            + "ORDER BY fecha_inicio DESC NULLS LAST, creado_en DESC, id",
            countQuery = "SELECT COUNT(*) FROM eventos WHERE " + FILTRO_CONSULTA,
            nativeQuery = true)
    Page<Evento> consultar(@Param("estado") String estado, Pageable pageable);

    /**
     * Eventos de un estado, con paginación (RF-MS04-13)
     *
     * @param estado estado del evento
     * @param pageable página y orden
     * @return la página de eventos
     */
    Page<Evento> findByEstado(EstadoEvento estado, Pageable pageable);

    /**
     * Eventos EN_EJECUCION que convocan al menos uno de los roles, ordenados por fecha de inicio (RF-MS04-01)
     *
     * @param rolesLiteral roles en formato de arreglo de PostgreSQL, p. ej. {ESTUDIANTE,PROFESOR}
     * @return los eventos activos que le aplican a esos roles
     */
    @Query(value = "SELECT * FROM eventos WHERE estado = 'EN_EJECUCION' "
            + "AND roles_convocados && CAST(:roles AS VARCHAR(20)[]) ORDER BY fecha_inicio, creado_en",
            nativeQuery = true)
    List<Evento> buscarActivosPorRoles(@Param("roles") String rolesLiteral);

    /**
     * Eventos EN_EJECUCION que convocan al menos uno de los roles del usuario (RF-MS04-01)
     *
     * @param roles nombres de los roles del usuario (catálogo en mayúsculas)
     * @return los eventos activos que le aplican, ordenados por fecha de inicio
     */
    default List<Evento> buscarActivosPorRoles(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return buscarActivosPorRoles("{" + String.join(",", roles) + "}");
    }
}
