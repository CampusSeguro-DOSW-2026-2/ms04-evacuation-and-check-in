# Rendimiento, concurrencia y usabilidad de MS04

Historia KAIR-606: verificar los RNF de rendimiento, concurrencia y usabilidad para declarar el MS04 integrable.

| RNF | Qué se verifica | Cómo |
|---|---|---|
| RNF-MS04-01 | Al menos el 80 % de los check-ins responde en menos de 5 s; los más lentos quedan en el log con su Correlation ID | k6 (`checkin_duracion_ms` p(80) < 5 s), JUnit y `PeticionLentaFilter` |
| RNF-MS04-02 | 500 check-ins simultáneos sin pérdida, sin duplicados ni errores, con un solo estado vigente por usuario | k6 (escenario `carga`) y JUnit |
| RNF-MS04-04 | Reportes simultáneos del mismo usuario se serializan, sin 409 ni 500 | k6 (escenario `mismo_usuario`) y JUnit |
| RNF-MS04-06 | Un check-in se refleja en el consolidado en menos de 5 s | k6 (`reflejo_consolidado_ms` máx < 5 s) y JUnit |
| RNF-MS04-07 | Reporte en máximo 3 interacciones | [prueba-usabilidad.md](prueba-usabilidad.md) (manual) |

Necesitan RF-MS04-02/03 (registrar y actualizar el check-in), RF-MS04-06 (consolidado) y RF-MS04-11 (recibir el
estado del evento). Hasta que estén implementados, las pruebas fallan con `501 NO_IMPLEMENTADO`.

## 1. Pruebas JUnit (sin HTTP, contra PostgreSQL real)

`src/test/.../rendimiento/RendimientoCheckinTest.java`. Tienen la etiqueta `rendimiento`: no corren con
`mvn verify` (ni en el pipeline), porque lanzan 500 hilos. Necesitan Docker en ejecución.

```bash
mvn verify -Prendimiento
```

Usan los estados que no llaman a otros servicios (Me estoy evacuando y No estoy en el campus). En el log quedan
los percentiles medidos (`Carga de 500 check-ins: p50=… p80=…`).

## 2. Prueba de carga HTTP con k6

Levanta MS04 y su base con el `docker-compose.yml` de la raíz, y agrega WireMock como Gateway: responde por MS01,
MS02 (puntos de encuentro ACTIVO), MS05 (solicitudes de asistencia) y MS08 (auditoría), con 20 a 120 ms de
latencia. Cubre los cuatro estados. Desde la raíz del repositorio, con el `.env` configurado:

```bash
docker compose -f docker-compose.yml -f rendimiento/docker-compose.rendimiento.yml up -d --build
```

```bash
docker compose -f docker-compose.yml -f rendimiento/docker-compose.rendimiento.yml run --rm k6
```

```bash
docker compose -f docker-compose.yml -f rendimiento/docker-compose.rendimiento.yml down -v
```

Cada corrida necesita una base limpia (`down -v`), porque el evento de la prueba tiene un id fijo.

**Escenarios** (`k6/carga-checkins.js`):

1. `carga`: 500 usuarios simulados reportan un estado y luego lo cambian, todos a la vez.
2. `mismo_usuario`: 20 reportes simultáneos del mismo usuario, en paralelo con la carga.
3. `reflejo`: con la carga terminada, 20 check-ins seguidos midiendo cuánto tarda el consolidado en contarlos.
4. Al final, comprueba que el consolidado tenga un estado vigente por usuario (521), que los cuatro estados sumen
   el total y que los resultados (RF-MS04-10) coincidan con el consolidado.

**Resultado:** k6 termina con código distinto de 0 si algún umbral falla. El resumen queda en
`rendimiento/resultados/resumen.json` (no se versiona). Para el informe de KAIR-680/681, copiar de ese resumen
`checkin_duracion_ms` (p(80), p(95), máx), `reflejo_consolidado_ms` (máx), `http_req_failed` y `checks`.

Las peticiones de más de 5 s se buscan en el log de MS04:

```bash
docker compose logs ms04 | grep "Petición lenta"
```

**Supuestos:** las rutas de MS01, MS02 y MS05 aún no están acordadas (decisiones abiertas 2 a 4 de los
requerimientos), así que los dobles responden a cualquier ruta `GET` (MS02) o `POST` (MS05) bajo `/api/v1/`. Cuando
se acuerden, ajustar `dobles/mappings/`.
