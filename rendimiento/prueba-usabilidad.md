# Prueba de usabilidad del reporte de estado (RNF-MS04-07)

Historia KAIR-606, subtarea KAIR-682.

**Requisito:** desde la pantalla inicial, el usuario completa el reporte de su estado en máximo 3 interacciones,
incluida la selección del punto de encuentro en *Estoy a salvo*. Los cuatro estados se muestran siempre con su
texto. Si no hay un evento activo, la pantalla inicial lo indica sin pasos adicionales.

## 1. Lo que el contrato de MS04 permite

El frontend necesita solo dos llamadas a MS04. El usuario no escribe nada: solo selecciona opciones.

| Momento | Llamada | Qué recibe el frontend |
|---|---|---|
| Al abrir la pantalla inicial (no cuenta como interacción) | `GET /api/v1/chi/eventos/activos` | Los eventos en ejecución que le aplican. Si la lista está vacía, muestra "No hay ningún evento activo." |
| Al abrir la pantalla inicial, si hay evento (no cuenta) | `GET /api/v1/chi/eventos/{eventoId}/checkins/propio` | Su estado vigente, si ya reportó (404 si no). |
| Al enviar | `POST /api/v1/chi/eventos/{eventoId}/checkins` | 201 en el primer reporte, 200 en un cambio. |

Los puntos de encuentro y las ubicaciones salen de MS02 y se cargan con la pantalla, no después de una interacción.

### Interacciones por estado

Una interacción es un toque o clic del usuario. Con un solo evento activo, el evento se selecciona solo.

| Estado | Interacciones | Total |
|---|---|---|
| Estoy a salvo | 1. Tocar "Estoy a salvo" · 2. Elegir el punto de encuentro · 3. Enviar | **3** |
| Me estoy evacuando | 1. Tocar "Me estoy evacuando" · 2. Enviar | **2** |
| No estoy en el campus | 1. Tocar "No estoy en el campus" · 2. Enviar | **2** |
| Requiero asistencia | 1. Tocar "Requiero asistencia" · 2. Elegir el lugar · 3. Enviar | **3** (ver nota) |

**Nota para el frontend (riesgo):** el formulario F-01 tiene dos campos para la ubicación, *tipo* y *lugar*. Si se
muestran como dos selectores, *Requiero asistencia* necesita 4 interacciones e incumple el RNF. Para cumplirlo, el
frontend debe mostrar **un solo selector de lugar agrupado por tipo** (Edificios, Espacios, Puntos de encuentro) y
enviar `ubicacion.tipo` y `ubicacion.id` a partir de esa única elección. El contrato de MS04 no cambia.

**Varios eventos activos (RF-MS04-01, A2):** elegir el evento suma una interacción. Es un caso raro y el RNF habla
del reporte desde la pantalla inicial; si el equipo considera que también debe cumplir las 3 interacciones, el
frontend puede preseleccionar el evento más reciente.

## 2. Protocolo de la prueba

**Participantes:** al menos 6 personas, una por cada rol que reporta su estado: ESTUDIANTE, PROFESOR,
ADMINISTRATIVO, BRIGADISTA, COORDINADOR y ORGANIZADOR.

**Preparación:**
1. Levantar MS04 con los dobles (`rendimiento/README.md`) o en el ambiente de pruebas con el frontend.
2. Crear un evento `EN_EJECUCION` que convoque los seis roles.
3. Iniciar sesión con cada participante y dejarlo en la pantalla inicial, sin explicar el flujo.

**Tareas** (en este orden, leídas en voz alta por el moderador):

| # | Tarea | Resultado esperado |
|---|---|---|
| T1 | "Reporte que está a salvo en el punto de encuentro X." | 201, estado SAFE con el punto correcto |
| T2 | "Ahora necesita asistencia en el edificio Y. Repórtelo." | 200, estado NEEDS_ASSISTANCE, solicitud enviada |
| T3 | "Ya salió del campus. Repórtelo." | 200, estado OFF_CAMPUS |
| T4 | "Está evacuando. Repórtelo." | 200, estado EVACUATING |
| T5 | Con el evento finalizado: "Revise si puede reportar su estado." | La pantalla indica que no hay evento activo, sin pasos adicionales |

**Qué se mide en cada tarea:** número de interacciones desde la pantalla inicial, tiempo hasta enviar, si la
completó sin ayuda y si eligió el texto correcto de los cuatro estados.

## 3. Criterio de aceptación

La prueba pasa si **todas** las tareas T1 a T4 se completan, por todos los participantes, en 3 interacciones o
menos y sin ayuda, y T5 no requiere ninguna interacción. Cualquier tarea por encima de 3 interacciones se reporta
como defecto del frontend con la tarea, el rol y las interacciones usadas.

## 4. Registro de resultados

Copiar esta tabla por cada participante.

| Rol | Tarea | Interacciones | Tiempo (s) | Sin ayuda (sí/no) | Estado registrado correcto (sí/no) | Observaciones |
|---|---|---|---|---|---|---|
| | T1 | | | | | |
| | T2 | | | | | |
| | T3 | | | | | |
| | T4 | | | | | |
| | T5 | | | | | |

**Resumen:** fecha, versión probada (commit), moderador, número de participantes, tareas que cumplieron (de 30),
defectos encontrados.
