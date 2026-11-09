package edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;

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
     * Condición que deja solo los check-ins vigentes que el BRIGADISTA puede ver (RN-MS04-14): SAFE en un punto de
     * encuentro de sus zonas y NEEDS_ASSISTANCE con la ubicación en uno de sus edificios, espacios o puntos de
     * encuentro. EVACUATING y OFF_CAMPUS no tienen ubicación, así que nunca cumplen. Con {@code restringido} en
     * falso no filtra nada. Se agrega al final de un WHERE y recibe los ids de MS02 como arreglos de PostgreSQL
     */
    String FILTRO_ZONAS = "AND (NOT CAST(:restringido AS BOOLEAN) OR ("
            + "(estado = 'SAFE' AND punto_encuentro_id = ANY (CAST(:puntos AS UUID[]))) "
            + "OR (estado = 'NEEDS_ASSISTANCE' AND ("
            + "(ubicacion_tipo = 'EDIFICIO' AND ubicacion_id = ANY (CAST(:edificios AS UUID[]))) "
            + "OR (ubicacion_tipo = 'ESPACIO' AND ubicacion_id = ANY (CAST(:espacios AS UUID[]))) "
            + "OR (ubicacion_tipo = 'PUNTO_ENCUENTRO' AND ubicacion_id = ANY (CAST(:puntos AS UUID[]))))))) ";

    /**
     * Filtros del listado de personas: evento, estado y punto de encuentro opcionales y zonas del BRIGADISTA
     */
    String FILTRO_PERSONAS = "evento_id = :eventoId "
            + "AND (CAST(:estado AS VARCHAR(20)) IS NULL OR estado = CAST(:estado AS VARCHAR(20))) "
            + "AND (CAST(:puntoEncuentro AS UUID) IS NULL OR punto_encuentro_id = CAST(:puntoEncuentro AS UUID)) "
            + FILTRO_ZONAS;

    /**
     * Cuenta en una sola consulta los estados vigentes de un evento por estado y, con SAFE, por punto de
     * encuentro: así los totales por estado y por punto salen del mismo instante. Los estados sin personas no
     * aparecen: quien arma el consolidado debe completarlos en 0 (RN-MS04-13). Ordena de mayor a menor total
     *
     * @param eventoId evento
     * @param restringido true para contar solo lo que el BRIGADISTA puede ver
     * @param puntos puntos de encuentro como arreglo de PostgreSQL, p. ej. {id1,id2}
     * @param edificios edificios como arreglo de PostgreSQL
     * @param espacios espacios como arreglo de PostgreSQL
     * @return total de personas por estado y punto de encuentro; el punto es null salvo con SAFE
     */
    @Query(value = "SELECT estado AS estado, punto_encuentro_id AS punto, COUNT(*) AS total "
            + "FROM v_checkins_vigentes WHERE evento_id = :eventoId " + FILTRO_ZONAS
            + "GROUP BY estado, punto_encuentro_id ORDER BY total DESC, punto_encuentro_id", nativeQuery = true)
    List<ConteoPorEstadoYPunto> contarVigentes(@Param("eventoId") UUID eventoId,
            @Param("restringido") boolean restringido, @Param("puntos") String puntos,
            @Param("edificios") String edificios, @Param("espacios") String espacios);

    /**
     * Cuenta los estados vigentes de un evento por estado y por punto de encuentro (RF-MS04-06)
     *
     * @param eventoId evento
     * @param restringido true para contar solo lo que el BRIGADISTA puede ver en sus zonas
     * @param puntos puntos de encuentro de las zonas del brigadista
     * @param edificios edificios de las zonas del brigadista
     * @param espacios espacios de las zonas del brigadista
     * @return total de personas por estado y punto de encuentro; el punto es null salvo con SAFE
     */
    default List<ConteoPorEstadoYPunto> contarVigentes(UUID eventoId, boolean restringido,
            Collection<UUID> puntos, Collection<UUID> edificios, Collection<UUID> espacios) {
        return contarVigentes(eventoId, restringido, arreglo(puntos), arreglo(edificios), arreglo(espacios));
    }

    /**
     * Estados vigentes de un evento, del reporte más reciente al más antiguo (RF-MS04-06)
     *
     * @param eventoId evento
     * @param estado código del estado a filtrar, null para todos
     * @param puntoEncuentro punto de encuentro a filtrar como texto UUID, null para todos
     * @param restringido true para listar solo lo que el BRIGADISTA puede ver
     * @param puntos puntos de encuentro como arreglo de PostgreSQL
     * @param edificios edificios como arreglo de PostgreSQL
     * @param espacios espacios como arreglo de PostgreSQL
     * @param pageable página pedida, sin orden: el orden lo fija la consulta
     * @return la página de check-ins vigentes
     */
    @Query(value = "SELECT * FROM v_checkins_vigentes WHERE " + FILTRO_PERSONAS
            + "ORDER BY registrado_en DESC, numero DESC",
            countQuery = "SELECT COUNT(*) FROM v_checkins_vigentes WHERE " + FILTRO_PERSONAS,
            nativeQuery = true)
    Page<Checkin> buscarVigentes(@Param("eventoId") UUID eventoId, @Param("estado") String estado,
            @Param("puntoEncuentro") String puntoEncuentro, @Param("restringido") boolean restringido,
            @Param("puntos") String puntos, @Param("edificios") String edificios,
            @Param("espacios") String espacios, Pageable pageable);

    /**
     * Estados vigentes de un evento filtrados por estado y punto de encuentro, del reporte más reciente al más
     * antiguo (RF-MS04-06)
     *
     * @param eventoId evento
     * @param estado estado a filtrar, null para todos
     * @param puntoEncuentroId punto de encuentro a filtrar, null para todos
     * @param restringido true para listar solo lo que el BRIGADISTA puede ver en sus zonas
     * @param puntos puntos de encuentro de las zonas del brigadista
     * @param edificios edificios de las zonas del brigadista
     * @param espacios espacios de las zonas del brigadista
     * @param pageable página pedida
     * @return la página de check-ins vigentes
     */
    default Page<Checkin> buscarVigentes(UUID eventoId, EstadoCheckin estado, UUID puntoEncuentroId,
            boolean restringido, Collection<UUID> puntos, Collection<UUID> edificios, Collection<UUID> espacios,
            Pageable pageable) {
        String estadoFiltro = null;
        if (estado != null) {
            estadoFiltro = estado.name();
        }
        String puntoFiltro = null;
        if (puntoEncuentroId != null) {
            puntoFiltro = puntoEncuentroId.toString();
        }
        return buscarVigentes(eventoId, estadoFiltro, puntoFiltro, restringido, arreglo(puntos), arreglo(edificios),
                arreglo(espacios), pageable);
    }

    /**
     * Convierte los ids al formato de arreglo de PostgreSQL que reciben las consultas
     *
     * @param ids ids de MS02, pueden ser ninguno
     * @return los ids entre llaves y separados por coma, {} si no hay ninguno
     */
    private static String arreglo(Collection<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(",", "{", "}"));
    }

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

    /**
     * Total de personas en un estado vigente y, con SAFE, en un punto de encuentro
     */
    interface ConteoPorEstadoYPunto {

        /**
         * @return código del estado del check-in
         */
        String getEstado();

        /**
         * @return punto de encuentro de MS02; null si el estado no es SAFE
         */
        UUID getPunto();

        /**
         * @return número de personas con ese estado y punto
         */
        long getTotal();
    }
}
