-- El escenario del evento (edificios y zonas) llega de MS06 con los nombres de MS02, que admiten hasta 100 caracteres
-- (RN-MS04-24). Las columnas pasan de 50 a 100 caracteres. En una base nueva corre después de 001; en una ya creada se
-- aplica una vez a mano (los scripts de database/ solo corren al crear el volumen).
ALTER TABLE eventos ALTER COLUMN escenario_edificios TYPE VARCHAR(100)[];

ALTER TABLE eventos ALTER COLUMN escenario_zonas TYPE VARCHAR(100)[];
