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
cuando existe el secreto `SONAR_TOKEN` y construye la imagen Docker (ver [CI/CD](#cicd)).

Las pruebas de rendimiento y concurrencia (RNF-MS04-01, 02, 04 y 06) no corren con `mvn verify` porque lanzan 500
hilos: se ejecutan con `mvn verify -Prendimiento`. La prueba de carga HTTP con k6 y el protocolo de la prueba de
usabilidad (RNF-MS04-07) están en [`rendimiento/`](rendimiento/README.md). Las peticiones de más de 5 s quedan en el
log como `Petición lenta` con su Correlation ID (umbral en `RENDIMIENTO_UMBRAL_LENTO_MS`).

## CI/CD

| Workflow | Cuándo | Qué hace |
|---|---|---|
| `ci.yml` | Cada push y PR | Compila y prueba (`mvn verify`), y analiza en SonarCloud si existe `SONAR_TOKEN`. Construye la imagen. Desde `develop` y `main` la publica en `ghcr.io/campusseguro-dosw-2026-2/ms04-evacuation-and-check-in` con `:develop` o `:latest` y `:sha-xxxxxxx`. |
| `ci.yml` → `deploy.yml` | Push a `develop` | Despliega esa imagen en el entorno de **prueba** (`:8443`). |
| `deploy-prod.yml` | A mano, desde `main` | Despliega en **producción** (`:443`) una imagen `sha-xxxxxxx` que ya pasó por prueba, sin reconstruirla. Otro tag anterior es el rollback. |

## Despliegue en la nube (prueba y producción)

MS04 corre en su propia VM, detrás del API Gateway. La guía completa, con los valores del GW, es
[Conexión al API Gateway](https://github.com/CampusSeguro-DOSW-2026-2/documentation/wiki/Conexi%C3%B3n-al-API-Gateway)
de la wiki:
* El firewall solo acepta la IP del GW en `443` (producción) y `8443` (prueba), y la IP de quien administra en `22`.
* Un Caddy de borde exige el certificado de cliente del GW (mTLS).
* Prueba y producción comparten la VM, cada una con su stack (MS04 + PostgreSQL) y sus secretos.

Los pasos 1 a 3 se hacen una sola vez. Los comandos marcados *(VM)* van en la VM por SSH; los demás, en su PC.

### 1. Borde Caddy (mTLS) *(VM)*
Con la carpeta `certs/` que entrega el equipo del GW (`prod/` y `test/`, cada una con `servidor.crt`,
`servidor.key` y `ca.pem`; nunca va al repositorio):
```bash
curl -fsSL https://get.docker.com | sudo sh && sudo usermod -aG docker "$USER"   # una vez; salga y vuelva a entrar
mkdir -p ~/campusseguro/edge && cd ~/campusseguro/edge
curl -fsSLO https://raw.githubusercontent.com/CampusSeguro-DOSW-2026-2/api-gateway/develop/infra/caddy/Caddyfile.microservicio
curl -fsSLO https://raw.githubusercontent.com/CampusSeguro-DOSW-2026-2/api-gateway/develop/infra/caddy/docker-compose.microservicio.yml
printf 'UPSTREAM_PROD=ms04-prod:8084\nUPSTREAM_TEST=ms04-test:8084\n' > .env
# copie aquí la carpeta certs/ (desde su PC: scp -i <llave>.pem -r certs ubuntu@<IP de la VM>:~/campusseguro/edge/)
chmod 600 certs/*/servidor.key
docker compose -f docker-compose.microservicio.yml up -d
```

### 2. Runner self-hosted
1. En GitHub: **Settings › Actions › Runners › New self-hosted runner** (Linux, x64). Ejecute en la VM los comandos
   de *Download* y configúrelo con la etiqueta `ms04` *(VM)*:
   ```bash
   ./config.sh --url https://github.com/CampusSeguro-DOSW-2026-2/ms04-evacuation-and-check-in --token <TOKEN> --labels ms04 --unattended
   sudo ./svc.sh install "$USER" && sudo ./svc.sh start
   ```
2. El repositorio es público: en **Settings › Actions › General › Fork pull request workflows from outside
   collaborators**, elija **Require approval for all external contributors**.

### 3. Environments de GitHub
Cree `ms04-test` (**Deployment branches**: solo `develop`) y `ms04-prod` (**Required reviewers** y
**Deployment branches**: solo `main`) en **Settings › Environments**:

| En `ms04-test` y `ms04-prod` | Tipo | Valor |
|---|---|---|
| `GATEWAY_URL` | Variable | Prueba: `https://campusseguro-gw.northcentralus.cloudapp.azure.com:8443`. Producción: `https://campusseguro-gw.northcentralus.cloudapp.azure.com`. |
| `MS04_EXTRA_ENV` | Variable (opcional) | Líneas `CLAVE=valor` extra, p. ej. `INTERNAL_ALLOWED_SERVICES=ms06` o `MS05_SOLICITUDES_PATH=...`. |
| `LOG_LEVEL`, `DEPLOY_DIR` | Variable (opcional) | `INFO` y `~/campusseguro/ms04` por defecto. |
| `DB_PASSWORD` | Secreto | `openssl rand -base64 24`, distinta por entorno. Solo se aplica cuando se crea la BD (primer despliegue). |
| `GATEWAY_SERVICE_KEY` | Secreto | La que entregó el equipo del GW para ese entorno (`GW_SERVICE_KEY_MS04` del GW). |

Ningún valor puede contener comillas simples (`'`).

### 4. Desplegar
1. **Prueba:** cada merge o push a `develop` publica la imagen y la despliega en `ms04-test` (job
   **desplegar-test** en *Actions*).
2. **Producción:** después de mezclar en `main`, **Actions › Desplegar a producción › Run workflow**, rama `main`,
   con el tag que ya funciona en prueba (`sha-` + 7 caracteres del commit de `develop`, en el log de
   *desplegar-test*), y apruebe el despliegue.

`infra/deploy.sh` verifica cada despliegue dentro de la VM: `/actuator/health` responde 200 por la red
`campus-edge` y el borde rechaza conexiones sin el certificado del GW. Si algo falla, el log del job muestra las
últimas líneas de MS04. Operación *(VM)*: `cd ~/campusseguro/ms04/test && docker compose ps && docker compose logs
--tail=100 ms04` (nunca `down -v`: borra la BD).

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
| RF-MS04-06, 07 y 08 Dashboard (KAIR-577, KAIR-578, KAIR-579) | `DashboardController` | `DashboardService` | RF-06 y 07 implementados; 08 pendiente |
| RF-MS04-09 Histórico (KAIR-580) | `HistorialController` | `HistorialService` | Pendiente |
| RF-MS04-10 Resultados (KAIR-583) | `ResultadosController` | `ResultadosService` | Implementado |
| RF-MS04-11 Recibir el estado del evento (KAIR-571) | `EventoInternoController` | `EstadoEventoService` | Implementado |
| RF-MS04-12 Detalle de check-ins para MS06 (KAIR-581) | `EventoInternoController` | `DetalleCheckinsService` | Implementado |
| RF-MS04-13 Consultar eventos (KAIR-582) | `ConsultaEventoController` | `ConsultaEventoService` | Implementado |

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
| Criterios de RF-MS04-01 a 13 | `mvn verify` | 10 de 13 implementados (01, 02, 03, 05, 06, 07, 10, 11, 12 y 13); faltan 04, 08 y 09 |
| RNF-MS04-01, 02, 04 y 06 | `mvn verify -Prendimiento` y k6 ([`rendimiento/`](rendimiento/README.md)) | Listas para ejecutar (ya están RF-02/03, 06 y 11) |
| RNF-MS04-03 (MS05 caído no impide el check-in) | Pruebas de RF-MS04-04 | Pendiente |
| RNF-MS04-05 (sin escrituras en un evento finalizado) | Pruebas de RF-MS04-02/03 y 11 | Pendiente |
| RNF-MS04-07 (3 interacciones) | [Prueba de usabilidad](rendimiento/prueba-usabilidad.md), manual con el frontend | Pendiente |
