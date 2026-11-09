// Prueba de carga del check-in de MS04 (historia KAIR-606, subtareas KAIR-680 y KAIR-681).
//
//   RNF-MS04-02  500 check-ins simultáneos sin pérdida, sin duplicados ni errores por saturación, con un solo
//                estado vigente por usuario
//   RNF-MS04-04  reportes simultáneos del mismo usuario se serializan: ninguno responde 409 ni 500
//   RNF-MS04-01  al menos el 80 % de los check-ins responde en menos de 5 s
//   RNF-MS04-06  un check-in se refleja en el consolidado en menos de 5 s
//
// Llama a MS04 directamente (sin Gateway) con las cabeceras de identidad, como en el README. MS01, MS02, MS05 y
// MS08 son dobles de WireMock (rendimiento/dobles). Se ejecuta con rendimiento/docker-compose.rendimiento.yml.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE = `${__ENV.MS04_URL || 'http://ms04:8084'}/api/v1/chi/eventos`;
const USUARIOS = Number(__ENV.USUARIOS || 500);
const EVENTO_ID = __ENV.EVENTO_ID || uuidDesde('e', 1);
const LIMITE_MS = 5000;

const ESTADOS = ['SAFE', 'EVACUATING', 'NEEDS_ASSISTANCE', 'OFF_CAMPUS'];
const PUNTOS = [
  '7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35',
  '9b2e5f3c-6d4a-4b9f-80e1-3f7a2d5b8c46',
  'a1c3e5f7-0b2d-4f6a-8c1e-3b5d7f9a1c2e',
];

const duracionCheckin = new Trend('checkin_duracion_ms', true);
const reflejoConsolidado = new Trend('reflejo_consolidado_ms', true);

export const options = {
  scenarios: {
    // RNF-MS04-02: cada usuario simulado reporta un estado y luego lo cambia, todos a la vez
    carga: {
      executor: 'per-vu-iterations',
      exec: 'carga',
      vus: USUARIOS,
      iterations: 1,
      maxDuration: '100s',
    },
    // RNF-MS04-04: 20 reportes simultáneos del mismo usuario, en paralelo con la carga
    mismo_usuario: {
      executor: 'per-vu-iterations',
      exec: 'mismoUsuario',
      vus: 20,
      iterations: 1,
      maxDuration: '100s',
    },
    // RNF-MS04-06: con la carga terminada, 20 check-ins seguidos midiendo cuándo los cuenta el consolidado
    reflejo: {
      executor: 'per-vu-iterations',
      exec: 'reflejo',
      vus: 1,
      iterations: 20,
      startTime: '110s',
      maxDuration: '120s',
    },
  },
  thresholds: {
    checks: ['rate==1.0'],
    'http_req_failed{tipo:checkin}': ['rate==0'],
    // RNF-MS04-01: el percentil 80 por debajo de 5 s equivale a "al menos el 80 % en menos de 5 s"
    checkin_duracion_ms: [`p(80)<${LIMITE_MS}`],
    reflejo_consolidado_ms: [`max<${LIMITE_MS}`],
  },
};

/** UUID v4 determinista a partir de un número, para reconocer a cada usuario simulado */
function uuidDesde(prefijo, n) {
  const sufijo = String(n).padStart(12, '0');
  return `${prefijo.repeat(8)}-0000-4000-8000-${sufijo}`.slice(0, 36);
}

/** UUID v4 aleatorio para el X-Correlation-ID */
function correlacion() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.floor(Math.random() * 16);
    return (c === 'x' ? r : (r % 4) + 8).toString(16);
  });
}

function cabeceras(usuarioId, rol) {
  return {
    'Content-Type': 'application/json',
    'X-User-Id': usuarioId,
    'X-User-Role': rol,
    'X-Correlation-ID': correlacion(),
  };
}

const COORDINADOR = uuidDesde('c', 1);
const ORGANIZADOR = uuidDesde('d', 1);

function cuerpo(estado, n) {
  if (estado === 'SAFE') {
    return { estado, punto_encuentro_id: PUNTOS[n % PUNTOS.length] };
  }
  if (estado === 'NEEDS_ASSISTANCE') {
    return { estado, ubicacion: { tipo: 'EDIFICIO', id: PUNTOS[n % PUNTOS.length] } };
  }
  return { estado };
}

function reportar(usuarioId, estado, n) {
  const respuesta = http.post(`${BASE}/${EVENTO_ID}/checkins`, JSON.stringify(cuerpo(estado, n)), {
    headers: cabeceras(usuarioId, 'ESTUDIANTE'),
    tags: { tipo: 'checkin' },
  });
  duracionCheckin.add(respuesta.timings.duration);
  return respuesta;
}

function consolidado() {
  const respuesta = http.get(`${BASE}/${EVENTO_ID}/consolidado`, {
    headers: cabeceras(COORDINADOR, 'COORDINADOR'),
    tags: { tipo: 'consulta' },
  });
  check(respuesta, { 'consolidado 200': (r) => r.status === 200 });
  return respuesta.json();
}

// MS06 informa a MS04 el evento en ejecución por la ruta interna (RF-MS04-11)
export function setup() {
  const respuesta = http.put(`${BASE}/${EVENTO_ID}/estado`, JSON.stringify({
    eventoId: EVENTO_ID,
    nombre: 'Prueba de carga del check-in',
    simulacro: true,
    estado: 'EN_EJECUCION',
    fechaInicio: new Date().toISOString(),
    escenario: { edificios: ['Bloque A'], zonas: ['Zona norte'] },
    rolesConvocados: ['ESTUDIANTE', 'PROFESOR', 'ADMINISTRATIVO'],
  }), {
    headers: { 'Content-Type': 'application/json', 'X-Service-Name': 'ms06', 'X-Correlation-ID': correlacion() },
  });
  if (respuesta.status !== 200 && respuesta.status !== 201) {
    throw new Error(`No se pudo crear el evento en MS04: HTTP ${respuesta.status} ${respuesta.body}`);
  }
}

export function carga() {
  const n = __VU;
  const usuario = uuidDesde('a', n);
  const primero = ESTADOS[n % ESTADOS.length];
  const segundo = ESTADOS[(n + 1) % ESTADOS.length];

  check(reportar(usuario, primero, n), { 'primer check-in 201': (r) => r.status === 201 });
  check(reportar(usuario, segundo, n), { 'cambio de estado 200': (r) => r.status === 200 });
}

export function mismoUsuario() {
  const estado = ESTADOS[__VU % 2 === 0 ? 1 : 3];
  check(reportar(uuidDesde('b', 1), estado, __VU), {
    'mismo usuario sin 409 ni 500': (r) => r.status === 200 || r.status === 201,
  });
}

export function reflejo() {
  const antes = consolidado().total_reportados;
  const inicio = Date.now();
  check(reportar(uuidDesde('f', __ITER + 1), 'EVACUATING', __ITER), { 'check-in de reflejo 201': (r) => r.status === 201 });
  while (consolidado().total_reportados <= antes && Date.now() - inicio < LIMITE_MS) {
    sleep(0.05);
  }
  reflejoConsolidado.add(Date.now() - inicio);
}

// Verificación final: sin pérdida ni duplicados (RNF-MS04-02) y totales coherentes (RN-MS04-13)
export function teardown() {
  const esperados = USUARIOS + 1 + 20;
  const c = consolidado();
  const t = c.totales_por_estado;
  const sumaPorPunto = c.por_punto_encuentro.reduce((suma, p) => suma + p.total, 0);
  check(c, {
    'consolidado: un estado vigente por usuario': () => c.total_reportados === esperados,
    'consolidado: los cuatro estados suman el total': () =>
      t.SAFE + t.EVACUATING + t.NEEDS_ASSISTANCE + t.OFF_CAMPUS === c.total_reportados,
    'consolidado: los puntos suman los SAFE': () => sumaPorPunto === t.SAFE,
  });

  const resultados = http.get(`${BASE}/${EVENTO_ID}/resultados`, { headers: cabeceras(ORGANIZADOR, 'ORGANIZADOR') });
  check(resultados, {
    'resultados 200': (r) => r.status === 200,
    'resultados: un primer check-in por usuario': (r) => r.json('primeros_checkins').length === esperados,
    'resultados: coinciden con el consolidado': (r) => r.json('total_reportados') === c.total_reportados,
  });
}
