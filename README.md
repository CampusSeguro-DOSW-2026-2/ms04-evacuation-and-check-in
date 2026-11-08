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

## Estructura y dónde va cada feature

Los controllers y services ya existen con el contrato completo (`api/swagger.yml` de la documentación). Lo que aún
no tiene lógica responde `501 NO_IMPLEMENTADO`. Cada feature del Jira tiene sus propias clases, para que no se
pisen entre sí: al implementar, se completa el service y se reemplaza su `ApiException.noImplementado(...)`.

| Feature (Jira) | Controller | Service |
|---|---|---|
| RF-MS04-01 Consultar eventos activos (KAIR-572) | `EventoActivoController` | `EventoActivoService` |
| RF-MS04-02 y RF-MS04-03 Registrar y actualizar el check-in (KAIR-573, KAIR-574) | `CheckinController` | `CheckinService` |
| RF-MS04-04 Solicitud de asistencia a MS05 (KAIR-575) | no tiene (lo llama `CheckinService`) | `SolicitudAsistenciaService` |
| RF-MS04-05 Consultar el propio estado (KAIR-576) | `EstadoPropioController` | `EstadoPropioService` |
| RF-MS04-06, 07 y 08 Dashboard (KAIR-577, KAIR-578, KAIR-579) | `DashboardController` | `DashboardService` |
| RF-MS04-09 Histórico (KAIR-580) | `HistorialController` | `HistorialService` |
| RF-MS04-10 Resultados (KAIR-583) | `ResultadosController` | `ResultadosService` |
| RF-MS04-11 Recibir el estado del evento (KAIR-571) | `EventoInternoController` | `EstadoEventoService` |
| RF-MS04-12 Detalle de check-ins para MS06 (KAIR-581) | `EventoInternoController` | `DetalleCheckinsService` |
| RF-MS04-13 Consultar eventos (KAIR-582) | `ConsultaEventoController` | `ConsultaEventoService` |

Piezas compartidas ya hechas, que no hay que volver a escribir:

* **Datos:** `entity/` y `repository/` (el estado vigente es la vista `v_checkins_vigentes`; `checkins` solo admite
  INSERT, un trigger lo garantiza; `CheckinRepository#bloquearUsuarioEnEvento` serializa los reportes de un usuario).
* **Errores:** lance `ApiException.noEncontrado(...)`, `.conflicto(...)`, `.reglaNegocio(...)`,
  `.dependenciaNoDisponible()`, etc.; `GlobalExceptionHandler` los convierte en el `ErrorResponse` de los Estándares.
* **Seguridad:** el controller recibe `RequesterIdentity` (de `X-User-Id` y `X-User-Role`) y verifica el permiso con
  `PermissionChecker`. Las rutas internas de MS06 las protege `SecurityConfig` por `X-Service-Name`.
* **Auditoría:** `AuditRecorder` envía el evento a MS08 sin afectar la respuesta si MS08 falla.
* **Paginación:** `Paginacion.validar(page, size)` y `PageResponse`.

Los ids que vienen de MS02 (punto de encuentro, edificio, espacio) son UUID, como en MS02.
