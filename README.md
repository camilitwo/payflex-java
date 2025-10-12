# Railway Spring Cloud Monorepo (carpetas independientes)
Cada subcarpeta es un proyecto Maven distinto. En Railway elige "Root Directory" al crear cada servicio.
Incluye: discovery-server, admin-server, api-gateway, auth-ms-java, merchant-service, payment-orchestrator.
Todos exponen `GET /health` y Actuator.
