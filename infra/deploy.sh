#!/usr/bin/env bash
# Despliega MS04 en su VM: stack ms04-<entorno> (MS04 + PostgreSQL) detrás del Caddy de borde, y verifica el despliegue.
# Lo ejecuta el runner self-hosted desde .github/workflows/deploy.yml con las variables y secretos del Environment
# ms04-<entorno> de GitHub. Es idempotente: volver a ejecutarlo con otro TAG es el rollback.
# El borde (Caddy con mTLS) se levanta una sola vez a mano, con la guía "Conexión al API Gateway" de la wiki.
set -euo pipefail

falta() { echo "::error::Falta la variable $1 (Environment ms04-${ENTORNO:-?} en GitHub)" >&2; exit 1; }
for VAR in ENTORNO DB_PASSWORD GATEWAY_URL GATEWAY_SERVICE_KEY; do
    [ -n "${!VAR:-}" ] || falta "$VAR"
done
[[ "$ENTORNO" =~ ^(test|prod)$ ]] || { echo "::error::ENTORNO debe ser test o prod" >&2; exit 1; }

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
# Misma etiqueta que publica el CI (docker/metadata-action, type=sha): los 7 primeros caracteres del commit.
TAG="${TAG:-sha-$(git -C "$REPO_DIR" rev-parse HEAD | cut -c1-7)}"
[[ "$TAG" =~ ^sha-[0-9a-f]{7}$ ]] || { echo "::error::TAG debe tener la forma sha-xxxxxxx (recibido: $TAG)" >&2; exit 1; }
IMAGE="${IMAGE:-ghcr.io/campusseguro-dosw-2026-2/ms04-evacuation-and-check-in}"
DEPLOY_DIR="${DEPLOY_DIR:-$HOME/campusseguro/ms04}"
PUERTO=$([ "$ENTORNO" = prod ] && echo 443 || echo 8443)
STACK_DIR="$DEPLOY_DIR/$ENTORNO"

docker network inspect campus-edge >/dev/null 2>&1 || {
    echo "::error::No existe la red campus-edge: levante primero el borde Caddy de la VM (README, Despliegue en la nube)" >&2
    exit 1
}

# Valores de una línea para el .env (entre comillas simples, sin interpolar): los saltos de línea van como \n.
una_linea() { printf '%s' "$1" | awk 'BEGIN{ORS=""} NR>1{print "\\n"} {print}'; }
linea_env() {
    local valor; valor="$(una_linea "$2")"
    [[ "$valor" != *"'"* ]] || { echo "::error::$1 no puede contener comillas simples" >&2; exit 1; }
    printf "%s='%s'\n" "$1" "$valor"
}

umask 077
mkdir -p "$STACK_DIR/database"

echo "== Archivos del despliegue ($ENTORNO, $TAG) en $STACK_DIR"
cp "$REPO_DIR/infra/docker-compose.prod.yml" "$STACK_DIR/docker-compose.yml"
cp "$REPO_DIR"/database/*.sql "$STACK_DIR/database/"
# Postgres lee los scripts con su propio usuario: la carpeta del esquema no lleva secretos.
chmod 755 "$STACK_DIR/database"
chmod 644 "$STACK_DIR"/database/*.sql
{
    linea_env ENTORNO "$ENTORNO"
    linea_env IMAGE "$IMAGE"
    linea_env TAG "$TAG"
    linea_env DB_PASSWORD "$DB_PASSWORD"
    linea_env GATEWAY_URL "$GATEWAY_URL"
    linea_env GATEWAY_SERVICE_KEY "$GATEWAY_SERVICE_KEY"
    linea_env LOG_LEVEL "${LOG_LEVEL:-INFO}"
} > "$STACK_DIR/.env"
printf '%s\n' "${MS04_EXTRA_ENV:-}" > "$STACK_DIR/extra.env"

echo "== Imagen $IMAGE:$TAG"
# En la VM el runner entrega GHCR_USER/GHCR_TOKEN; PULL_POLICY=missing solo sirve para probar con una imagen local.
if [ -n "${GHCR_TOKEN:-}" ]; then
    printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u "${GHCR_USER:?Falta GHCR_USER}" --password-stdin >/dev/null
fi
C=(docker compose --project-directory "$STACK_DIR")
"${C[@]}" config --quiet
"${C[@]}" up -d --remove-orphans --pull "${PULL_POLICY:-always}"
[ -z "${GHCR_TOKEN:-}" ] || docker logout ghcr.io >/dev/null

# El firewall y el mTLS impiden probar desde fuera: se prueba dentro de la VM, por la red campus-edge.
echo "== Verificación de http://ms04-$ENTORNO:8084/actuator/health (espera hasta 3 min: arranque de MS04 y la BD)"
CURL=(docker run --rm --network campus-edge curlimages/curl:8.11.1 -s -o /dev/null -w '%{http_code}')
for _ in $(seq 1 36); do
    if [ "$("${CURL[@]}" "http://ms04-$ENTORNO:8084/actuator/health" || true)" = 200 ]; then
        # Sin el certificado del GW, el borde debe cortar la conexión en TLS (curl 35, 55 o 56), nunca responder.
        # Otro código (p. ej. 0 = respondió, 7 = nadie escucha) significa que el borde falta o no exige el certificado.
        RC=0; curl -sk -o /dev/null "https://127.0.0.1:$PUERTO/actuator/health" || RC=$?
        [[ "$RC" =~ ^(35|55|56)$ ]] || {
            echo "::error::El borde :$PUERTO no rechazó la conexión sin certificado de cliente (curl terminó con $RC)"
            exit 1
        }
        echo "Despliegue verificado: health 200 y borde :$PUERTO exige el certificado del GW ($ENTORNO, $TAG)"
        exit 0
    fi
    sleep 5
done
echo "::error::MS04 no respondió 200 en /actuator/health. Revise: docker compose --project-directory $STACK_DIR logs"
"${C[@]}" logs --tail=50 ms04 || true
exit 1
