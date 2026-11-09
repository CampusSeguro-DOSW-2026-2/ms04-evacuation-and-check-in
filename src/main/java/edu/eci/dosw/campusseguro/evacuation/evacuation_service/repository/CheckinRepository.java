package edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;

/**
 * Acceso a los check-ins. Extiende {@link Repository} y no JpaRepository a propósito: los check-ins solo se
 * agregan, así que el código no tiene operaciones de modificación ni de eliminación sobre ellos (RN-MS04-07,
 * RNF-MS04-04). La base de datos lo refuerza con un trigger
 */
public interface CheckinRepository extends Repository<Checkin, UUID> {

    /**
     * Agrega un check-in nuevo. La base de datos asigna numero y registrado_en
     *
     * @param checkin check-in nuevo
     * @return el check-in guardado
     */
    Checkin save(Checkin checkin);

    /**
     * Envía a la base de datos los check-ins pendientes de la transacción, para que el check-in recién guardado
     * tenga ya su {@code numero} y su {@code registrado_en}
     */
    void flush();

    /**
     * Busca un check-in por su id
     *
     * @param id id del check-in
     * @return el check-in, si existe
     */
    Optional<Checkin> findById(UUID id);

    /**
     * Estado vigente de un usuario en un evento: su check-in más reciente (vista v_checkins_vigentes)
     *
     * @param eventoId evento
     * @param usuarioId usuario (X-User-Id)
     * @return el check-in vigente, si el usuario ya reportó en el evento
     */
    @Query(value = "SELECT * FROM v_checkins_vigentes WHERE evento_id = :eventoId AND usuario_id = :usuarioId",
            nativeQuery = true)
    Optional<Checkin> buscarVigente(@Param("eventoId") UUID eventoId, @Param("usuarioId") UUID usuarioId);

    /**
     * Cuenta los estados vigentes de un evento por estado. Los estados sin personas no aparecen: quien arma el
     * consolidado debe completarlos en 0 (RN-MS04-13)
     *
     * @param eventoId evento
     * @return total de personas por estado vigente
     */
    @Query(value = "SELECT estado AS estado, COUNT(*) AS total FROM v_checkins_vigentes "
            + "WHERE evento_id = :eventoId GROUP BY estado", nativeQuery = true)
    List<ConteoPorEstado> contarVigentesPorEstado(@Param("eventoId") UUID eventoId);

    /**
     * Cuenta las personas cuyo estado vigente es SAFE, agrupadas por punto de encuentro (RN-MS04-13). Solo las
     * personas a salvo se cuentan por punto
     *
     * @param eventoId evento
     * @return total de personas a salvo por punto de encuentro, del punto con más personas al de menos
     */
    @Query(value = "SELECT punto_encuentro_id AS puntoEncuentroId, COUNT(*) AS total FROM v_checkins_vigentes "
            + "WHERE evento_id = :eventoId AND estado = 'SAFE' "
            + "GROUP BY punto_encuentro_id ORDER BY total DESC, punto_encuentro_id", nativeQuery = true)
    List<ConteoPorPunto> contarSafePorPuntoEncuentro(@Param("eventoId") UUID eventoId);

    /**
     * Personas que reportaron Requiero asistencia al menos una vez en el evento, aunque su estado vigente ya sea
     * otro, con su solicitud de asistencia (RF-MS04-10). Ordenadas por el primer reporte de Requiero asistencia
     *
     * @param eventoId evento
     * @return una fila por persona, con el id de la solicitud en MS05 y su estado de envío (null si no tiene)
     */
    @Query(value = "SELECT c.usuario_id AS usuarioId, s.solicitud_ms05_id AS solicitudId, "
            + "s.estado_envio AS estadoEnvio "
            + "FROM checkins c LEFT JOIN solicitudes_asistencia s "
            + "ON s.evento_id = c.evento_id AND s.usuario_id = c.usuario_id "
            + "WHERE c.evento_id = :eventoId AND c.estado = 'NEEDS_ASSISTANCE' "
            + "GROUP BY c.usuario_id, s.solicitud_ms05_id, s.estado_envio "
            + "ORDER BY MIN(c.registrado_en), MIN(c.numero)", nativeQuery = true)
    List<SolicitudPorUsuario> buscarSolicitantesDeAsistencia(@Param("eventoId") UUID eventoId);

    /**
     * Fecha y hora del primer check-in de cada usuario en el evento: {@code MIN(registrado_en)} (RF-MS04-10).
     * Hay una fila por cada usuario con estado vigente
     *
     * @param eventoId evento
     * @return el primer check-in de cada usuario, del más antiguo al más reciente
     */
    @Query(value = "SELECT usuario_id AS usuarioId, MIN(registrado_en) AS primerCheckinEn "
            + "FROM checkins WHERE evento_id = :eventoId "
            + "GROUP BY usuario_id ORDER BY MIN(registrado_en), MIN(numero)", nativeQuery = true)
    List<PrimerCheckinPorUsuario> buscarPrimerosCheckins(@Param("eventoId") UUID eventoId);

    /**
     * Toma un bloqueo asesor transaccional sobre la clave: los reportes simultáneos con la misma clave se
     * atienden uno tras otro y el bloqueo se libera al terminar la transacción (RNF-MS04-04)
     *
     * @param clave clave del bloqueo
     * @return siempre 1
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:clave, 0))) AS bloqueo",
            nativeQuery = true)
    Integer bloquear(@Param("clave") String clave);

    /**
     * Serializa los reportes de un mismo usuario en un mismo evento (RF-MS04-03, A5). Debe llamarse al inicio
     * de la transacción del check-in
     *
     * @param eventoId evento
     * @param usuarioId usuario
     */
    default void bloquearUsuarioEnEvento(UUID eventoId, UUID usuarioId) {
        bloquear("checkin:" + eventoId + ":" + usuarioId);
    }

    /**
     * Total de personas en un estado vigente
     */
    interface ConteoPorEstado {

        /**
         * @return código del estado del check-in
         */
        String getEstado();

        /**
         * @return número de personas con ese estado vigente
         */
        long getTotal();
    }

    /**
     * Total de personas a salvo en un punto de encuentro
     */
    interface ConteoPorPunto {

        /**
         * @return punto de encuentro de MS02
         */
        UUID getPuntoEncuentroId();

        /**
         * @return número de personas cuyo estado vigente es SAFE en ese punto
         */
        long getTotal();
    }

    /**
     * Persona que reportó Requiero asistencia al menos una vez, con su solicitud
     */
    interface SolicitudPorUsuario {

        /**
         * @return id del usuario
         */
        UUID getUsuarioId();

        /**
         * @return id de la solicitud en MS05; null mientras no haya llegado o si no hay solicitud
         */
        String getSolicitudId();

        /**
         * @return estado de envío de la solicitud; null si no hay solicitud
         */
        String getEstadoEnvio();
    }

    /**
     * Primer check-in de un usuario en un evento
     */
    interface PrimerCheckinPorUsuario {

        /**
         * @return id del usuario
         */
        UUID getUsuarioId();

        /**
         * @return fecha y hora de su primer check-in
         */
        Instant getPrimerCheckinEn();
    }
}
