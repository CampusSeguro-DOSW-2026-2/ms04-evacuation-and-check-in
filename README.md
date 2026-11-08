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
