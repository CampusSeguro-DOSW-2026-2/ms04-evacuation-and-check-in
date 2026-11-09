# MS04 - EVACUATION AND CHECK-IN

MS04 - Reporte del estado de las personas durante simulacros y emergencias (Estoy a salvo, Me estoy evacuando,
Requiero asistencia, No estoy en el campus) y consolidación por punto de encuentro.

| Name                            | GitHub Username                   |
|---------------------------------|-----------------------------------|
| Daniel Santiago Morales Perdomo | `Fukuyaku88`                      |
| Edgar Daniel Ruiz Patiño        | `DaniRuiz11`                      |
| Miguel Ángel Acero Laverde      | `Miguelim101`                     |
| Thomas Sebastian García Gomez   | `thomassebastiangarciagomez-ctrl` |
| Rodrigo Gualtero                | `Rodrigoreg92`                    |

---

## Documentación

* Requerimientos, formularios, modelo de datos y contrato OpenAPI: repositorio
  [documentation](https://github.com/CampusSeguro-DOSW-2026-2/documentation), carpeta `ms04-evacuation-check-in`.
* Reglas transversales (cabeceras, errores, llamadas internas, auditoría):
  [Estándares de Integración](https://github.com/CampusSeguro-DOSW-2026-2/documentation/wiki/Est%C3%A1ndares-de-Integraci%C3%B3n-y-Contratos-Transversales).
* Permisos: [Matriz Rol‐Permiso](https://github.com/CampusSeguro-DOSW-2026-2/documentation/wiki/Matriz-Rol%E2%80%90Permiso).

## Tecnología

Java 21, Spring Boot 4.1, Spring Data JPA, PostgreSQL propia, Maven. Puerto interno `8084`.

## Configuración

Toda la configuración llega por variables de entorno. Para desarrollo local, copie `.env.example` como `.env` y
complete los valores (en especial `DB_PASSWORD`). El archivo `.env` nunca se sube al repositorio.

## Ejecución local

```bash
# 1. Variables: copie .env.example como .env y defina DB_PASSWORD
cp .env.example .env

# 2. Base de datos y MS04 en Docker (la base queda en el puerto 5434 del equipo, solo en 127.0.0.1)
docker compose up -d --build

# 3. Comprobar que arrancó: 200 {"status":"UP"}
curl http://localhost:8084/actuator/health

# 4. Sin el Gateway, la identidad se envía a mano (el Gateway las inyecta en producción)
curl -H "X-User-Id: 3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42" -H "X-User-Role: ESTUDIANTE" \
     http://localhost:8084/api/v1/chi/eventos/activos

# 5. Bajar los contenedores (con -v también se borra la base de datos)
docker compose down
```

Los scripts de `database/` crean el esquema solo la primera vez que se crea el volumen. Si cambian, hay que bajar con
`docker compose down -v` para recrearlo.

Para ejecutar la aplicación desde el IDE o con Maven, deje corriendo solo la base (`docker compose up -d ms04-db`) y
use el `.env` (la aplicación lo lee al arrancar): `mvn spring-boot:run`.

## Pruebas

```bash
mvn verify
```

Necesita **Docker en ejecución**: las pruebas de acceso a datos y la de la aplicación completa levantan un PostgreSQL
real con Testcontainers y le aplican los scripts de `database/`. Genera la cobertura en
`target/site/jacoco/jacoco.xml`. El pipeline (`.github/workflows/ci.yml`) hace lo mismo, analiza con SonarCloud
cuando existe el secreto `SONAR_TOKEN` y construye la imagen Docker.

Las pruebas de rendimiento y concurrencia (RNF-MS04-01, 02, 04 y 06) no corren con `mvn verify` porque lanzan 500
hilos: se ejecutan con `mvn verify -Prendimiento`. La prueba de carga HTTP con k6 y el protocolo de la prueba de
usabilidad (RNF-MS04-07) están en [`rendimiento/`](rendimiento/README.md). Las peticiones de más de 5 s quedan en el
log como `Petición lenta` con su Correlation ID (umbral en `RENDIMIENTO_UMBRAL_LENTO_MS`).

## Estructura y dónde va cada feature

Los controllers y services ya existen con el contrato completo (`api/swagger.yml` de la documentación). Lo que aún
no tiene lógica responde `501 NO_IMPLEMENTADO`. Cada feature del Jira tiene sus propias clases, para que no se
pisen entre sí: al implementar, se completa el service y se reemplaza su `ApiException.noImplementado(...)`.

| Feature (Jira) | Controller | Service | Estado |
|---|---|---|---|
| RF-MS04-01 Consultar eventos activos (KAIR-572) | `EventoActivoController` | `EventoActivoService` | Implementado |
| RF-MS04-02 y RF-MS04-03 Registrar y actualizar el check-in (KAIR-573, KAIR-574) | `CheckinController` | `CheckinService` | Implementado |
| RF-MS04-04 Solicitud de asistencia a MS05 (KAIR-575) | no tiene (lo llama `CheckinService`) | `SolicitudAsistenciaService` | Pendiente |
| RF-MS04-05 Consultar el propio estado (KAIR-576) | `EstadoPropioController` | `EstadoPropioService` | Implementado |
| RF-MS04-06, 07 y 08 Dashboard (KAIR-577, KAIR-578, KAIR-579) | `DashboardController` | `DashboardService` | RF-06 implementado; 07 y 08 pendientes |
| RF-MS04-09 Histórico (KAIR-580) | `HistorialController` | `HistorialService` | Pendiente |
| RF-MS04-10 Resultados (KAIR-583) | `ResultadosController` | `ResultadosService` | Implementado |
| RF-MS04-11 Recibir el estado del evento (KAIR-571) | `EventoInternoController` | `EstadoEventoService` | Implementado |
| RF-MS04-12 Detalle de check-ins para MS06 (KAIR-581) | `EventoInternoController` | `DetalleCheckinsService` | Pendiente |
| RF-MS04-13 Consultar eventos (KAIR-582) | `ConsultaEventoController` | `ConsultaEventoService` | Pendiente |

Piezas compartidas ya hechas, que no hay que volver a escribir:

* **Datos:** `entity/` y `repository/` (el estado vigente es la vista `v_checkins_vigentes`; `checkins` solo admite
  INSERT, un trigger lo garantiza; `CheckinRepository#bloquearUsuarioEnEvento` serializa los reportes de un usuario).
* **Errores:** lance `ApiException.noEncontrado(...)`, `.conflicto(...)`, `.reglaNegocio(...)`,
  `.dependenciaNoDisponible()`, etc.; `GlobalExceptionHandler` los convierte en el `ErrorResponse` de los Estándares.
* **Seguridad:** el controller recibe `RequesterIdentity` (de `X-User-Id` y `X-User-Role`) y verifica el permiso con
  `PermissionChecker`. Las rutas internas de MS06 las protege `SecurityConfig` por `X-Service-Name`.
* **Auditoría:** `AuditRecorder` envía el evento a MS08 sin afectar la respuesta si MS08 falla.
* **Paginación:** `Paginacion.validar(page, size)` y `PageResponse`.
* **Población objetivo (MS01):** `PoblacionObjetivoClient` devuelve los usuarios ACTIVOS de los roles convocados
  (RN-MS04-15), o vacío si MS01 no responde. Lo usan los resultados (RF-MS04-10) y sirve para los pendientes
  (RF-MS04-07). La ruta de MS01 (`GET /api/v1/users/by-role`) está pendiente de acordar (decisión abierta n.º 2).

Los ids que vienen de MS02 (punto de encuentro, edificio, espacio) son UUID, como en MS02.

## Condición de cierre

MS04 es integrable cuando pasan los criterios de aceptación de RF-MS04-01 a RF-MS04-13 y se verifican RNF-MS04-01
a RNF-MS04-07 (historia KAIR-606). Al implementar una feature, actualice su columna *Estado* en la tabla anterior.

| Verificación | Cómo | Estado |
|---|---|---|
| Criterios de RF-MS04-01 a 13 | `mvn verify` | 7 de 13 implementados (01, 02, 03, 05, 06, 10 y 11) |
| RNF-MS04-01, 02, 04 y 06 | `mvn verify -Prendimiento` y k6 ([`rendimiento/`](rendimiento/README.md)) | Listas para ejecutar (ya están RF-02/03, 06 y 11) |
| RNF-MS04-03 (MS05 caído no impide el check-in) | Pruebas de RF-MS04-04 | Pendiente |
| RNF-MS04-05 (sin escrituras en un evento finalizado) | Pruebas de RF-MS04-02/03 y 11 | Pendiente |
| RNF-MS04-07 (3 interacciones) | [Prueba de usabilidad](rendimiento/prueba-usabilidad.md), manual con el frontend | Pendiente |
