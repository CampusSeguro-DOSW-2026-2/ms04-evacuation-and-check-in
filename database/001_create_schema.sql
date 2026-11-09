-- MS04 - Evacuación y check-in: tablas (documentation/ms04-evacuation-check-in/3_modelo-datos.md)
-- Cambio respecto al documento: los ids que vienen de MS02 (punto de encuentro, edificio, espacio) son UUID,
-- porque MS02 migró sus identificadores de BIGINT a UUID.

-- Eventos que MS06 le informa a MS04 (RF-MS04-11). El id es el eventoId que asigna MS06.
CREATE TABLE eventos (

    id UUID PRIMARY KEY,

    nombre VARCHAR(150) NOT NULL,

    simulacro BOOLEAN NOT NULL,

    estado VARCHAR(12) NOT NULL,

    fecha_inicio TIMESTAMPTZ DEFAULT NULL,

    fecha_cierre TIMESTAMPTZ DEFAULT NULL,

    roles_convocados VARCHAR(20)[] NOT NULL,

    escenario_edificios VARCHAR(50)[] NOT NULL DEFAULT '{}',

    escenario_zonas VARCHAR(50)[] NOT NULL DEFAULT '{}',

    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),

    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_eventos_estado
        CHECK (estado IN ('BORRADOR', 'PROGRAMADO', 'EN_EJECUCION', 'FINALIZADO')),

    -- Un evento que no es simulacro nunca está en BORRADOR ni PROGRAMADO (RN-MS04-20)
    CONSTRAINT chk_eventos_simulacro
        CHECK (simulacro OR estado IN ('EN_EJECUCION', 'FINALIZADO')),

    -- La fecha de inicio es nula si y solo si el evento está en BORRADOR (RN-MS04-24)
    CONSTRAINT chk_eventos_fecha_inicio
        CHECK ((fecha_inicio IS NULL) = (estado = 'BORRADOR')),

    -- La fecha de cierre existe si y solo si el evento está FINALIZADO
    CONSTRAINT chk_eventos_fecha_cierre
        CHECK ((fecha_cierre IS NOT NULL) = (estado = 'FINALIZADO')),

    -- Al menos un rol convocado y solo roles que pueden reportar su estado (RN-MS04-24)
    CONSTRAINT chk_eventos_roles_convocados
        CHECK (cardinality(roles_convocados) >= 1
               AND roles_convocados <@ ARRAY['ESTUDIANTE', 'PROFESOR', 'ADMINISTRATIVO',
                                             'BRIGADISTA', 'COORDINADOR', 'ORGANIZADOR']::VARCHAR(20)[])
);

-- Un check-in por cada estado reportado. Solo admite INSERT: el estado vigente es el más reciente (RN-MS04-07)
CREATE TABLE checkins (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Desempata dos check-ins con la misma registrado_en: el de mayor numero es el más reciente
    numero BIGINT GENERATED ALWAYS AS IDENTITY,

    evento_id UUID NOT NULL,

    usuario_id UUID NOT NULL,

    estado VARCHAR(20) NOT NULL,

    punto_encuentro_id UUID DEFAULT NULL,

    ubicacion_tipo VARCHAR(20) DEFAULT NULL,

    ubicacion_id UUID DEFAULT NULL,

    -- clock_timestamp() y no now(): now() devuelve la hora de inicio de la transacción
    registrado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT fk_checkins_evento
        FOREIGN KEY (evento_id) REFERENCES eventos (id) ON DELETE RESTRICT,

    CONSTRAINT uq_checkins_numero UNIQUE (numero),

    CONSTRAINT chk_checkins_estado
        CHECK (estado IN ('SAFE', 'EVACUATING', 'NEEDS_ASSISTANCE', 'OFF_CAMPUS')),

    -- Punto de encuentro solo y siempre con SAFE (RN-MS04-04)
    CONSTRAINT chk_checkins_punto_encuentro
        CHECK ((punto_encuentro_id IS NOT NULL) = (estado = 'SAFE')),

    CONSTRAINT chk_checkins_ubicacion_tipo
        CHECK (ubicacion_tipo IS NULL OR ubicacion_tipo IN ('EDIFICIO', 'ESPACIO', 'PUNTO_ENCUENTRO')),

    -- Ubicación solo y siempre con NEEDS_ASSISTANCE (RN-MS04-09)
    CONSTRAINT chk_checkins_ubicacion
        CHECK ((ubicacion_tipo IS NOT NULL) = (estado = 'NEEDS_ASSISTANCE')
               AND (ubicacion_id IS NOT NULL) = (ubicacion_tipo IS NOT NULL))
);

-- Solicitudes de asistencia generadas desde el check-in y su estado de envío a MS05 (RF-MS04-04)
CREATE TABLE solicitudes_asistencia (

    -- Id propio; se envía a MS05 como llave de idempotencia (RN-MS04-12)
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    checkin_id UUID NOT NULL,

    evento_id UUID NOT NULL,

    usuario_id UUID NOT NULL,

    ubicacion_tipo VARCHAR(20) NOT NULL,

    ubicacion_id UUID NOT NULL,

    -- Id que devuelve MS05; su tipo aún no está definido en el contrato de MS05
    solicitud_ms05_id VARCHAR(64) DEFAULT NULL,

    estado_envio VARCHAR(16) NOT NULL DEFAULT 'PENDIENTE_ENVIO',

    intentos INTEGER NOT NULL DEFAULT 0,

    ultimo_intento_en TIMESTAMPTZ DEFAULT NULL,

    reportada_en TIMESTAMPTZ NOT NULL DEFAULT now(),

    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_solicitudes_checkin
        FOREIGN KEY (checkin_id) REFERENCES checkins (id) ON DELETE RESTRICT,

    CONSTRAINT fk_solicitudes_evento
        FOREIGN KEY (evento_id) REFERENCES eventos (id) ON DELETE RESTRICT,

    -- Una solicitud por usuario y evento (RN-MS04-11)
    CONSTRAINT uq_solicitudes_evento_usuario UNIQUE (evento_id, usuario_id),

    CONSTRAINT chk_solicitudes_ubicacion_tipo
        CHECK (ubicacion_tipo IN ('EDIFICIO', 'ESPACIO', 'PUNTO_ENCUENTRO')),

    CONSTRAINT chk_solicitudes_estado_envio
        CHECK (estado_envio IN ('ENVIADA', 'PENDIENTE_ENVIO', 'RECHAZADA')),

    -- El id de MS05 es obligatorio cuando la solicitud quedó ENVIADA (RN-MS04-10)
    CONSTRAINT chk_solicitudes_id_ms05
        CHECK (estado_envio <> 'ENVIADA' OR solicitud_ms05_id IS NOT NULL),

    CONSTRAINT chk_solicitudes_intentos
        CHECK (intentos >= 0)
);
