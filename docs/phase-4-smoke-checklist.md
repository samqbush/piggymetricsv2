# Phase 4 — Gateway edge & observability smoke checklist

Manual verification for the Spring Cloud Gateway edge rewrite (Zuul → SCG) and the
Micrometer/Prometheus/Grafana observability swap. The automated `GatewayRoutingTest`
pins routing/header parity; this checklist covers the live, wired-up stack.

## Prerequisites
- JDK 21 (`export JAVA_HOME=<jdk-21>`).
- Docker running. On Docker Desktop 29+ (Apple Silicon):
  `export DOCKER_HOST=unix://$HOME/.docker/run/docker.sock`.
- A `.env` file with the service passwords (`CONFIG_SERVICE_PASSWORD`,
  `NOTIFICATION_SERVICE_PASSWORD`, `STATISTICS_SERVICE_PASSWORD`,
  `ACCOUNT_SERVICE_PASSWORD`, `MONGODB_PASSWORD`).

## Bring up the stack (source-built, Phase 4 images)
```bash
mvn -DskipTests clean package
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build
```
The dev override builds `config`, `registry`, `gateway`, and the three data
services from source (Java 21 images) and pins `auth-service` to the published
`sqshq/piggymetrics-auth-service` oracle image (it is quarantined until Phase 5).

Wait until `registry` shows all services registered (http://localhost:8761).

## Routing parity (through the gateway on :80 → gateway :4000)

| # | Request | Expected |
|---|---------|----------|
| 1 | `curl -i http://localhost/` | 200, PiggyMetrics UI `index.html` (static served by SCG) |
| 2 | `curl -i http://localhost/css/style.css` | 200, static asset |
| 3 | `curl -i http://localhost/accounts/demo` | Routed to account-service (401/redirect from its security, **not** 404 — proves routing + `/accounts` prefix preserved) |
| 4 | `curl -i http://localhost/statistics/demo` | Routed to statistics-service (not 404) |
| 5 | `curl -i http://localhost/notifications/recipients/current` | Routed to notification-service (not 404) |
| 6 | `curl -i http://localhost/nonexistent` | 404 (no route, no static match) |

### Header forwarding
```bash
# Obtain a token from the legacy auth oracle, then call a protected route:
curl -s http://localhost/uaa/oauth/token -d grant_type=password \
  -d username=demo -d password=demo -u browser: | jq -r .access_token
curl -i http://localhost/accounts/current -H "Authorization: Bearer <token>"
```
Expect the downstream service to accept the `Authorization` header (parity with
Zuul's cleared `sensitiveHeaders`).

> **`/uaa/**` route:** targets the legacy auth oracle image. Full end-to-end login
> is **verified in Phase 5** when auth-service is rewritten. In Phase 4, confirm
> only that `/uaa/**` routes to `auth-service:5000` (does not 404 at the gateway).

## Observability

| # | Check | Expected |
|---|-------|----------|
| 7 | `curl -s http://localhost:6000/accounts/actuator/prometheus \| head` | Prometheus exposition text (metrics) |
| 8 | http://localhost:9090/targets | All targets (account/statistics/notification/gateway) **UP** |
| 9 | `curl -s 'http://localhost:9090/api/v1/query?query=jvm_memory_used_bytes'` | Non-empty result with an `application` label |
| 10 | http://localhost:3000 (admin/admin) → dashboard **"PiggyMetrics — Resilience4j & JVM"** | Panels render; JVM/HTTP panels show data |
| 11 | Trigger a circuit breaker (stop statistics-service, hit `/accounts/*` that calls it) | `resilience4j_circuitbreaker_*` metrics change in Grafana |

## Pass criteria
- Rows 1–6: routing + static parity (no unexpected 404s; `/nonexistent` is 404).
- Header forwarding confirmed.
- Rows 7–11: Prometheus scrapes all targets UP; Grafana dashboard shows JVM +
  circuit-breaker metrics.
- `monitoring` (:9000) and `turbine-stream-service` (:8989) are **gone** — no
  containers, no ports.

## Residual risk (recorded)
- `/uaa/**` live login deferred to Phase 5 (auth-service quarantined).
- `/actuator/prometheus` is currently reachable because Phase 3 service security
  permits all requests; when Phase 5 restores resource-server security, Prometheus
  scrape access must be explicitly permitted.
