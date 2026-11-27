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
  [documentation](https://github.com/CampusSeguro-DOSW-2026-2/documentation), carpeta
  [`ms04-evacuation-and-check-in`](https://github.com/CampusSeguro-DOSW-2026-2/documentation/tree/main/ms04-evacuation-and-check-in).
* Reglas transversales (cabeceras, errores, llamadas internas, auditoría):
  [`integration/standards.md`](https://github.com/CampusSeguro-DOSW-2026-2/documentation/blob/main/integration/standards.md).
* Permisos:
  [`integration/permissions-matrix.md`](https://github.com/CampusSeguro-DOSW-2026-2/documentation/blob/main/integration/permissions-matrix.md).

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

# 4. Sin el Gateway, la identidad se envía a mano (el Gateway la inyecta en producción)
curl -H "X-User-Id: 3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42" -H "X-User-Role: ESTUDIANTE" \
     -H "X-User-Permission: ms04:checkin:read_own,ms04:checkin:write,ms04:assistance:request" \
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

No necesita Docker ni una base de datos: todas las pruebas usan JUnit 5, Mockito y MockMvc. Genera la cobertura en
`target/site/jacoco/jacoco.xml`. El pipeline (`.github/workflows/ci.yml`) hace lo mismo, analiza con SonarCloud
cuando existe el secreto `SONAR_TOKEN` y construye la imagen Docker (ver [CI/CD](#cicd)).

| Tipo | Qué cubre | Clases |
|---|---|---|
| Unitarias de services | Reglas de negocio de cada requerimiento, con repositorios y clientes simulados con Mockito | `service/*Test` |
| Contrato HTTP | Rutas, permisos de la matriz, validación de entradas y forma de las respuestas, con `@WebMvcTest` y la cadena de seguridad real | `controller/*HttpTest`, `security/SecurityHttpTest` |
| Clientes salientes | Llamadas a MS01, MS02, MS05 y MS08 contra un servidor HTTP local que hace de Gateway | `client/*Test`, `audit/AuditEventPublisherTest` |
| Persistencia sin base de datos | Coherencia entre los scripts de `database/` y las entidades, restricciones, vista del estado vigente, trigger de solo inserción y consultas y bloqueos de los repositorios | `repository/*Test`, `entity/EntitiesTest` |
| Aplicación | Arranque con la configuración real de `application.yaml` (sin fuente de datos ni JPA) y los valores de esa configuración | `ApplicationTest`, `ApplicationConfigurationTest` |

La prueba de carga HTTP con k6 y el protocolo de la prueba de usabilidad (RNF-MS04-07) están en
[`rendimiento/`](rendimiento/README.md). Las peticiones de más de 5 s quedan en el log como `Petición lenta` con su
Correlation ID (umbral en `RENDIMIENTO_UMBRAL_LENTO_MS`).

## CI/CD

| Workflow | Cuándo | Qué hace |
|---|---|---|
| `ci.yml` | Cada push y PR | Compila y prueba (`mvn verify`), y analiza en SonarCloud si existe `SONAR_TOKEN`. Construye la imagen. Desde `develop` y `main` la publica en `ghcr.io/campusseguro-dosw-2026-2/ms04-evacuation-and-check-in` con `:develop` o `:latest` y `:sha-xxxxxxx`. |
| `ci.yml` → `deploy.yml` | Push a `develop` | Despliega esa imagen en el entorno de **prueba** (`:8443`). |
| `deploy-prod.yml` | A mano, desde `main` | Despliega en **producción** (`:443`) una imagen `sha-xxxxxxx` que ya pasó por prueba, sin reconstruirla. Otro tag anterior es el rollback. |

## Despliegue en la nube (prueba y producción)

MS04 corre en su propia VM, detrás del API Gateway. La guía completa, con los valores del GW, es
[`integration/gateway-connection.md`](https://github.com/CampusSeguro-DOSW-2026-2/documentation/blob/main/integration/gateway-connection.md):
* El firewall solo acepta la IP del GW en `443` (producción) y `8443` (prueba), y la IP de quien administra en `22`.
* Un Caddy de borde exige el certificado de cliente del GW (mTLS).
* Prueba y producción comparten la VM, cada una con su stack y sus secretos.
* La base de datos está en otra VM de la misma VPC, con una PostgreSQL por entorno (prod en `5432`, prueba en
  `5433`). MS04 llega a ella por la IP privada y el Security Group de la BD solo acepta a la VM de MS04.

Los pasos 1 a 4 se hacen una sola vez. Los comandos marcados *(VM)* van en la VM de MS04 por SSH, los marcados
*(VM BD)* en la VM de la base de datos, y los demás en su PC.

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
| `DB_HOST` | Variable | IP privada de la VM de la BD (paso 4). |
| `DB_PORT` | Variable (opcional) | `5432` en `ms04-prod` y `5433` en `ms04-test` por defecto. |
| `DB_PASSWORD` | Secreto | `openssl rand -base64 24`, distinta por entorno. Debe ser la misma de `DB_PASSWORD_PROD` o `DB_PASSWORD_TEST` en la VM de la BD (paso 4). |
| `GATEWAY_SERVICE_KEY` | Secreto | La que entregó el equipo del GW para ese entorno (`GW_SERVICE_KEY_MS04` del GW). |

Ningún valor puede contener comillas simples (`'`).

### 4. VM de base de datos *(VM BD)*
1. En AWS, una instancia EC2 `t3.micro` (Ubuntu LTS, 10 GB gp3, crédito *standard*) en la **misma VPC** que la VM
   de MS04. Su Security Group acepta, con origen el Security Group de la VM de MS04, `5432-5433` (PostgreSQL) y
   `22` (SSH). Nunca `0.0.0.0/0`. Para instalar la VM puede abrir `22` a *Mi IP* temporalmente.
2. En la VM:
   ```bash
   curl -fsSL https://get.docker.com | sudo sh && sudo usermod -aG docker "$USER"   # una vez; salga y vuelva a entrar
   git clone https://github.com/CampusSeguro-DOSW-2026-2/ms04-evacuation-and-check-in.git ~/campusseguro/ms04-db
   cd ~/campusseguro/ms04-db
   (umask 077 && printf 'IP_PRIVADA=%s\nDB_PASSWORD_PROD=%s\nDB_PASSWORD_TEST=%s\n' \
       "$(hostname -I | awk '{print $1}')" '<DB_PASSWORD de ms04-prod>' '<DB_PASSWORD de ms04-test>' > infra/db/.env)
   docker compose -f infra/db/docker-compose.db.yml up -d
   docker compose -f infra/db/docker-compose.db.yml ps   # prod y test en estado healthy
   ```
   Los scripts de `database/` crean el esquema solo la primera vez. Un script nuevo se aplica a mano en cada
   entorno: `docker compose -f infra/db/docker-compose.db.yml exec -T prod psql -U ms04_user -d ms04_db
   < database/<script>.sql` (y lo mismo con `test`), después de `git pull`.
3. Cuando todo funcione, quite la regla temporal de `22` y la IP pública de la VM (*Interfaz de red › Administrar
   direcciones IP*). Desde entonces se entra saltando por la VM de MS04:
   `ssh -J ubuntu@<IP pública de MS04> ubuntu@<IP privada de la BD>`.

**Migración desde la BD que corría junto a MS04** (una vez por entorno, antes del primer despliegue con la BD
separada). Las contraseñas actuales están en `~/campusseguro/ms04/<entorno>/.env` de la VM de MS04. Con prod
(para prueba: `test`, `ms04-test-db-1` y `5433`) *(VM)*:
```bash
docker compose --project-directory ~/campusseguro/ms04/prod stop ms04
docker exec ms04-prod-db-1 pg_dump -U ms04_user -Fc ms04_db \
  | docker run --rm -i -e PGPASSWORD='<DB_PASSWORD de prod>' postgres:17.6-alpine \
      pg_restore -h <IP privada de la BD> -p 5432 -U ms04_user -d ms04_db --clean --if-exists --no-owner
```
Luego despliegue (paso 5): el contenedor `db` anterior se elimina, pero su volumen (`ms04-prod_datos`) se conserva
como respaldo hasta que lo borre con `docker volume rm`.

### 5. Desplegar
1. **Prueba:** cada merge o push a `develop` publica la imagen y la despliega en `ms04-test` (job
   **desplegar-test** en *Actions*).
2. **Producción:** después de mezclar en `main`, **Actions › Desplegar a producción › Run workflow**, rama `main`,
   con el tag que ya funciona en prueba (`sha-` + 7 caracteres del commit de `develop`, en el log de
   *desplegar-test*), y apruebe el despliegue.

`infra/deploy.sh` verifica cada despliegue dentro de la VM: `/actuator/health` responde 200 por la red
`campus-edge` y el borde rechaza conexiones sin el certificado del GW. Si algo falla, el log del job muestra las
últimas líneas de MS04. Antes de desplegar comprueba que la BD acepte conexiones en `DB_HOST:DB_PORT`.
Operación *(VM)*: `cd ~/campusseguro/ms04/test && docker compose ps && docker compose logs --tail=100 ms04`.
Operación *(VM BD)*: `cd ~/campusseguro/ms04-db && docker compose -f infra/db/docker-compose.db.yml ps` (nunca
`down -v`: borra las bases de datos).

## Estructura

Cada feature del Jira tiene sus propias clases:

| Feature (Jira) | Controller | Service |
|---|---|---|
| RF-MS04-01 Consultar eventos activos (KAIR-572) | `EventoActivoController` | `EventoActivoService` |
| RF-MS04-02 y RF-MS04-03 Registrar y actualizar el check-in (KAIR-573, KAIR-574) | `CheckinController` | `CheckinService` |
| RF-MS04-04 Solicitud de asistencia a MS05 (KAIR-575) | no tiene (lo llama `CheckinService`; el reenvío periódico es `ReenvioSolicitudesTarea`) | `SolicitudAsistenciaService` |
| RF-MS04-05 Consultar el propio estado (KAIR-576) | `EstadoPropioController` | `EstadoPropioService` |
| RF-MS04-06, 07 y 08 Dashboard (KAIR-577, KAIR-578, KAIR-579) | `DashboardController` | `DashboardService` |
| RF-MS04-09 Histórico (KAIR-580) | `HistorialController` | `HistorialService` |
| RF-MS04-10 Resultados (KAIR-583) | `ResultadosController` | `ResultadosService` |
| RF-MS04-11 Recibir el estado del evento (KAIR-571) | `EventoInternoController` | `EstadoEventoService` |
| RF-MS04-12 Detalle de check-ins para MS06 (KAIR-581) | `EventoInternoController` | `DetalleCheckinsService` |
| RF-MS04-13 Consultar eventos (KAIR-582) | `ConsultaEventoController` | `ConsultaEventoService` |

Piezas compartidas:

* **Datos:** `entity/` y `repository/` (el estado vigente es la vista `v_checkins_vigentes`; `checkins` solo admite
  INSERT, un trigger lo garantiza; `CheckinRepository#bloquearUsuarioEnEvento` serializa los reportes de un usuario).
* **Errores:** `ApiException.noEncontrado(...)`, `.conflicto(...)`, `.reglaNegocio(...)`,
  `.dependenciaNoDisponible()`, etc.; `GlobalExceptionHandler` los convierte en el `ErrorResponse` de los Estándares.
* **Seguridad:** el controller recibe `RequesterIdentity` (de `X-User-Id`, `X-User-Role` y `X-User-Permission`) y
  verifica el permiso con `PermissionChecker`. Las rutas internas de MS06 las protege `SecurityConfig` por
  `X-Service-Name`.
* **Auditoría:** `AuditRecorder` envía el evento a MS08 sin afectar la respuesta si MS08 falla.
* **Paginación:** `Paginacion.validar(page, size)` y `PageResponse`.
* **Población objetivo (MS01):** `PoblacionObjetivoClient` consulta `GET /api/v1/users/by-role` de MS01 (RF-MS01-09)
  y devuelve los usuarios ACTIVOS de los roles convocados (RN-MS04-15), o vacío si MS01 no responde. La usan los
  pendientes (RF-MS04-07) y los resultados (RF-MS04-10).

Los ids que vienen de MS02 (punto de encuentro, edificio, espacio) son UUID, como en MS02.

## Verificación de los requerimientos

| Verificación | Cómo |
|---|---|
| Criterios de RF-MS04-01 a RF-MS04-13 | `mvn verify` |
| RNF-MS04-01, 02, 04 y 06 (tiempos de respuesta, carga y concurrencia) | k6 ([`rendimiento/`](rendimiento/README.md)) |
| RNF-MS04-03 (MS05 caído no impide el check-in) | `CheckinServiceTest` y `SolicitudAsistenciaServiceTest` |
| RNF-MS04-05 (sin escrituras en un evento finalizado) | `CheckinServiceTest`, `EstadoEventoServiceTest` y `SchemaScriptsTest` |
| RNF-MS04-07 (3 interacciones) | [Prueba de usabilidad](rendimiento/prueba-usabilidad.md), manual con el frontend |
