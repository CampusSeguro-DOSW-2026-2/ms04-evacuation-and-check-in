-- Los eventos reales siguen los mismos estados que los simulacros (RN-MS04-20): MS04 guarda el estado que envía
-- MS06, dueño del ciclo de vida, también BORRADOR y PROGRAMADO. En una base nueva corre después de 001; en una ya
-- creada se aplica una vez a mano (los scripts de database/ solo corren al crear el volumen).
ALTER TABLE eventos DROP CONSTRAINT IF EXISTS chk_eventos_simulacro;
