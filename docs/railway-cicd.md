# Configuración de despliegues selectivos en Railway

Este repositorio contiene múltiples microservicios Java gestionados como un monorepo. El flujo de trabajo `Deploy changed services to Railway` permite que el CI/CD despliegue únicamente el servicio que haya cambiado en un commit o _pull request_, evitando que Railway reconstruya todo el grupo.

## ¿Cómo funciona?

1. Al ejecutar el workflow se inspeccionan los archivos modificados mediante [`dorny/paths-filter`](https://github.com/dorny/paths-filter).
2. Solo se añaden a la matriz de despliegue los servicios que tengan cambios en su directorio (`admin-server`, `api-gateway`, `auth-ms-java`, `discovery-server`, `merchant-service` y `payment-orchestrator`).
3. Por cada servicio detectado se ejecuta Railway CLI apuntando al subdirectorio correspondiente (`--base-directory`).

Si no se detectan cambios en los microservicios, el flujo termina sin desplegar nada.

## Requisitos

Configura los siguientes _secrets_ en el repositorio para que el flujo pueda autenticarse en Railway:

| Secret | Descripción |
| --- | --- |
| `RAILWAY_TOKEN` | Token personal generado con `railway login`. |
| `RAILWAY_PROJECT_ID` | Identificador del proyecto en Railway (`railway project`). |
| `RAILWAY_ENVIRONMENT_ID` | Identificador del entorno a desplegar (opcional si se usa el _default_). |

> **Nota:** Si usas diferentes proyectos de Railway por servicio, crea múltiples workflows o sustituye `railway up --service` por el comando específico que necesites.

## Ajustes recomendados

- Si trabajas con ramas distintas a `main`, `develop` o `release/*`, añade las ramas en la sección `on.push.branches` del workflow.
- Personaliza el comando de despliegue (`railway up`) para adaptarlo a tu pipeline (por ejemplo, `railway deploy`).
- Puedes añadir validaciones previas (tests, builds) dentro del mismo job antes de ejecutar Railway.

Con esta configuración cada commit o _pull request_ activará únicamente el despliegue del servicio afectado.
