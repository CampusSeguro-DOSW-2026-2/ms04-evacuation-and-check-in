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
     * Lee el evento con bloqueo compartido (SELECT ... FOR SHARE) en la transacción del check-in: así un
     * FINALIZADO espera a que termine el check-in en curso y ninguno se cuela después (3_modelo-datos.md)
     *
     * @param id eventoId
     * @return el evento bloqueado para lectura, si existe
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select e from Evento e where e.id = :id")
    Optional<Evento> buscarParaCheckin(@Param("id") UUID id);

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
            + "AND roles_convocados && CAST(:roles AS VARCHAR(20)[]) ORDER BY fecha_inicio", nativeQuery = true)
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
