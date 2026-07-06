[![CI](https://github.com/samqbush/piggymetricsv2/actions/workflows/ci.yml/badge.svg)](https://github.com/samqbush/piggymetricsv2/actions/workflows/ci.yml)

# Piggy Metrics

Piggy Metrics is a simple financial advisor app built to demonstrate the [Microservice Architecture Pattern](http://martinfowler.com/microservices/) using Spring Boot, Spring Cloud and Docker. The project is intended as a tutorial, but you are welcome to fork it and turn it into something else!

<br>

![](https://cloud.githubusercontent.com/assets/6069066/13864234/442d6faa-ecb9-11e5-9929-34a9539acde0.png)
![Piggy Metrics](https://cloud.githubusercontent.com/assets/6069066/13830155/572e7552-ebe4-11e5-918f-637a49dff9a2.gif)

## Modernization at a glance

This fork has been **fully modernized** off the original EOL stack (Java 8, Spring Boot
2.0.3, Spring Cloud Finchley, Netflix Zuul/Hystrix/Ribbon/Turbine, `spring-security-oauth2`).
The microservice decomposition is preserved — that is the demo's whole point — but every
EOL framework and Netflix component has been upgraded or swapped for its modern equivalent.
See [`MODERNIZATION_PLAN.md`](MODERNIZATION_PLAN.md) for the full phased roadmap and
[`ARCHITECTURE.md`](ARCHITECTURE.md) for the audited before-state.

| Concern | Before (legacy) | After (this fork) |
|---------|-----------------|-------------------|
| Runtime | Java 8 | **Java 21 (LTS)** |
| Platform | Spring Boot 2.0.3 | **Spring Boot 3.3.x** (Jakarta namespace) |
| Spring Cloud | Finchley | **2023.0.x (Leyton)** |
| API Gateway / edge | Netflix Zuul 1 | **Spring Cloud Gateway** (+ BFF: authorization_code + PKCE, TokenRelay) |
| Circuit breaker | Hystrix | **Resilience4j** (Spring Cloud CircuitBreaker) |
| Load balancing | Ribbon | **Spring Cloud LoadBalancer** |
| Metrics / dashboard | Hystrix Dashboard + Turbine | **Micrometer + Prometheus + Grafana** (Actuator) |
| Distributed tracing | Spring Cloud Sleuth | **Micrometer Tracing** |
| Auth server | `spring-security-oauth2` `@EnableAuthorizationServer` | **Spring Authorization Server** (RSA-signed JWT) |
| Resource servers | `@EnableResourceServer` + remote check-token | **OAuth2 Resource Server (JWT)**, local validation |
| Service-to-service auth | `OAuth2FeignRequestInterceptor` / `OAuth2RestTemplate` | **OAuth2 Client `client_credentials`** |
| Token model | opaque `InMemoryTokenStore` | **stateless signed JWT** |
| Password / secret encoding | `NoOpPasswordEncoder` / `{noop}` | **BCrypt** |
| Service discovery | Netflix Eureka | **Eureka** (kept, upgraded in place) |
| Config | Cloud Config (native) | **Cloud Config (native)** (kept, upgraded in place) |
| Messaging | RabbitMQ (Bus + Hystrix stream) | **RabbitMQ** (Cloud Bus refresh only) |
| Test database | flapdoodle embedded mongo | **Testcontainers MongoDB** |
| MongoDB image | `mongo:3` | **`mongo:7`** |
| Container base image | `java:8-jre` | **`eclipse-temurin:21-jre`** |
| CI | Travis CI | **GitHub Actions** (`build-java-21`) |

> The in-scope reactor is now **7 modules** (config, registry, account/statistics/notification-service,
> gateway, auth-service). The Hystrix-only `monitoring` and `turbine-stream-service`
> modules were **removed** — observability moved to Micrometer/Prometheus/Grafana.

## Functional services

Piggy Metrics is decomposed into three core microservices. All of them are independently deployable applications organized around certain business domains.

<img width="880" alt="Functional services" src="https://cloud.githubusercontent.com/assets/6069066/13900465/730f2922-ee20-11e5-8df0-e7b51c668847.png">

#### Account service
Contains general input logic and validation: incomes/expenses items, savings and account settings.

Method	| Path	| Description	| User authenticated	| Available from UI
------------- | ------------------------- | ------------- |:-------------:|:----------------:|
GET	| /accounts/{account}	| Get specified account data	|  | 	
GET	| /accounts/current	| Get current account data	| × | ×
GET	| /accounts/demo	| Get demo account data (pre-filled incomes/expenses items, etc)	|   | 	×
PUT	| /accounts/current	| Save current account data	| × | ×
POST	| /accounts/	| Register new account	|   | ×


#### Statistics service
Performs calculations on major statistics parameters and captures time series for each account. Datapoint contains values normalized to base currency and time period. This data is used to track cash flow dynamics during the account lifetime.

Method	| Path	| Description	| User authenticated	| Available from UI
------------- | ------------------------- | ------------- |:-------------:|:----------------:|
GET	| /statistics/{account}	| Get specified account statistics	          |  | 	
GET	| /statistics/current	| Get current account statistics	| × | × 
GET	| /statistics/demo	| Get demo account statistics	|   | × 
PUT	| /statistics/{account}	| Create or update time series datapoint for specified account	|   | 


#### Notification service
Stores user contact information and notification settings (reminders, backup frequency etc). Scheduled worker collects required information from other services and sends e-mail messages to subscribed customers.

Method	| Path	| Description	| User authenticated	| Available from UI
------------- | ------------------------- | ------------- |:-------------:|:----------------:|
GET	| /notifications/settings/current	| Get current account notification settings	| × | ×	
PUT	| /notifications/settings/current	| Save current account notification settings	| × | ×

#### Notes
- Each microservice has its own database, so there is no way to bypass API and access persistence data directly.
- MongoDB is used as a primary database for each of the services.
- All services are talking to each other via the Rest API

## Infrastructure
[Spring cloud](https://spring.io/projects/spring-cloud) provides powerful tools for developers to quickly implement common distributed systems patterns -
<img width="880" alt="Infrastructure services" src="https://cloud.githubusercontent.com/assets/6069066/13906840/365c0d94-eefa-11e5-90ad-9d74804ca412.png">
### Config service
[Spring Cloud Config](https://docs.spring.io/spring-cloud-config/reference/) is a horizontally scalable centralized configuration service for distributed systems. It uses a pluggable repository layer that currently supports local storage, Git, and Subversion.

In this project, we use the `native profile`, which simply loads config files from the local classpath. You can see the `shared` directory in [Config service resources](config/src/main/resources). Now, when the Notification service requests its configuration, the Config service responds with `shared/notification-service.yml` and `shared/application.yml` (which is shared between all client applications).

##### Client side usage
Just build Spring Boot application with `spring-cloud-starter-config` dependency, autoconfiguration will do the rest.

Now you don't need any embedded properties in your application. Just provide `bootstrap.yml` with application name and Config service url:
```yml
spring:
  application:
    name: notification-service
  cloud:
    config:
      uri: http://config:8888
      fail-fast: true
```

##### With Spring Cloud Config, you can change application config dynamically. 
For example, the [EmailService bean](notification-service/src/main/java/com/piggymetrics/notification/service/EmailServiceImpl.java) is annotated with `@RefreshScope`. That means you can change the e-mail text and subject without rebuilding and restarting the Notification service.

First, change required properties in Config server. Then make a refresh call to the Notification service:
`curl -H "Authorization: Bearer #token#" -XPOST http://127.0.0.1:8000/notifications/refresh`

You could also use Repository [webhooks to automate this process](http://cloud.spring.io/spring-cloud-config/spring-cloud-config.html#_push_notifications_and_spring_cloud_bus)

##### Notes
- `@RefreshScope` doesn't work with `@Configuration` classes and doesn't ignores `@Scheduled` methods
- `fail-fast` property means that Spring Boot application will fail startup immediately, if it cannot connect to the Config Service.

### Auth service
Authorization responsibilities are extracted into a separate server, which issues [OAuth2](https://tools.ietf.org/html/rfc6749) **JWT** access tokens for the backend resource services. The Auth server is used for user authorization as well as for secure machine-to-machine communication inside the perimeter.

> **Before → after:** the legacy auth-service was built on the removed
> `spring-security-oauth2` library (`@EnableAuthorizationServer`, opaque
> `InMemoryTokenStore`, `NoOpPasswordEncoder`, the deprecated **password** grant,
> and a per-request remote check-token round trip). It has been **rewritten on
> [Spring Authorization Server](https://spring.io/projects/spring-authorization-server)**,
> issuing **RSA-signed JWTs**. Client secrets and user passwords are now **BCrypt**
> encoded.

Two flows are used:

- **User login (browser):** [`authorization_code` + PKCE](https://datatracker.ietf.org/doc/html/rfc7636) via a **Backend-for-Frontend (BFF)** at the gateway. The password grant is gone, so the gateway logs the user in and holds the session; it relays the session's JWT downstream via the `TokenRelay` filter.
- **Service-to-service:** the [`Client Credentials`](https://tools.ietf.org/html/rfc6749#section-4.4) grant, obtained through Spring's OAuth2 Client and attached to Feign calls inside the perimeter.

Each resource service is now a stateless **OAuth2 Resource Server** that validates JWTs **locally** against the Auth server's JWK Set (`jwk-set-uri`) — no remote check-token call per request. Each PiggyMetrics token carries scopes: `server` for backend services and `ui` for the browser. We use the `@PreAuthorize` annotation to protect controllers from external access:

``` java
@PreAuthorize("hasAuthority('SCOPE_server')")
@RequestMapping(value = "/statistics/{name}", method = RequestMethod.GET)
public List<DataPoint> getStatisticsByAccountName(@PathVariable String name) {
	return statisticsService.findByAccountName(name);
}
```

### API Gateway
The API Gateway is a single entry point into the system, used to handle requests and route them to the appropriate backend service or by [aggregating results from a scatter-gather call](http://techblog.netflix.com/2013/01/optimizing-netflix-api.html). It can also be used for authentication, insights, stress and canary testing, service migration, static response handling and active traffic management.

> **Before → after:** the legacy edge used **Netflix Zuul 1** (`@EnableZuulProxy`),
> which is removed from modern Spring Cloud. The edge has been **rewritten on
> [Spring Cloud Gateway](https://docs.spring.io/spring-cloud-gateway/reference/)**.
> It also acts as the **BFF** — it logs the browser in (authorization_code + PKCE)
> and relays the JWT downstream with the `TokenRelay` default filter.

Spring Cloud Gateway routes are declared with `Path` predicates. To preserve parity with the old Zuul config (every route used `stripPrefix: false`), no `StripPrefix` filter is applied, so the full path is forwarded downstream unchanged. Here is the routing for the Notification service:

```yml
spring:
  cloud:
    gateway:
      default-filters:
        - TokenRelay=
      routes:
        - id: notification-service
          uri: lb://notification-service
          predicates:
            - Path=/notifications/**
```

That means all requests starting with `/notifications` are routed to the Notification service. There are no hardcoded addresses: `lb://` tells the gateway to resolve the target through [Service discovery](#service-discovery) and balance across instances with [Spring Cloud LoadBalancer](#load-balancer-circuit-breaker-and-http-client), described below.

### Service Discovery

Service Discovery allows automatic detection of the network locations for all registered services. These locations might have dynamically assigned addresses due to auto-scaling, failures or upgrades.

The key part of Service discovery is the Registry. In this project, we use Netflix Eureka. Eureka is a good example of the client-side discovery pattern, where client is responsible for looking up the locations of available service instances and load balancing between them.

With Spring Boot, you can easily build a Eureka Registry using the `spring-cloud-starter-netflix-eureka-server` dependency, the `@EnableEurekaServer` annotation and simple configuration properties.

Client support enabled with `@EnableDiscoveryClient` annotation a `bootstrap.yml` with application name:
``` yml
spring:
  application:
    name: notification-service
```

This service will be registered with the Eureka Server and provided with metadata such as host, port, health indicator URL, home page etc. Eureka receives heartbeat messages from each instance belonging to the service. If the heartbeat fails over a configurable timetable, the instance will be removed from the registry.

Also, Eureka provides a simple interface where you can track running services and a number of available instances: `http://localhost:8761`

### Load balancer, Circuit breaker and Http client

> **Before → after:** the legacy stack used **Netflix Ribbon** (client-side load
> balancing), **Hystrix** (circuit breaker) and **Feign** wired to both. Ribbon and
> Hystrix are EOL and removed from modern Spring Cloud. They have been swapped for
> **Spring Cloud LoadBalancer** and **Resilience4j**; **OpenFeign** stays.

#### Spring Cloud LoadBalancer
Spring Cloud LoadBalancer is the built-in client-side load balancer (Ribbon's replacement). Compared to a traditional load balancer, there is no additional network hop — you contact the desired service directly. It natively integrates with Service Discovery: the [Eureka Client](#service-discovery) provides a dynamic list of available servers so the load balancer can balance between them.

#### Resilience4j
[Resilience4j](https://resilience4j.readme.io/) is a lightweight fault-tolerance library that implements the [Circuit Breaker Pattern](http://martinfowler.com/bliki/CircuitBreaker.html) (Hystrix's replacement, integrated via Spring Cloud CircuitBreaker). It gives us control over latency and network failures while communicating with other services. The main idea is to stop cascading failures in a distributed environment — fail fast and recover as soon as possible, important aspects of a fault-tolerant system that can self-heal.

Resilience4j exposes metrics through **Micrometer**, which we scrape with **Prometheus** and visualize in **Grafana** (see [Monitoring](#monitoring)).

#### Feign
Feign is a declarative HTTP client which seamlessly integrates with Spring Cloud LoadBalancer and Resilience4j. A single `spring-cloud-starter-openfeign` dependency plus the `@EnableFeignClients` annotation gives us a full set of tools — load balancing, circuit breaking (with `spring.cloud.openfeign.circuitbreaker.enabled`) and an HTTP client — with reasonable defaults.

Here is an example from the Account service:

``` java
@FeignClient(name = "statistics-service", fallback = StatisticsServiceClientFallback.class)
public interface StatisticsServiceClient {

	@RequestMapping(method = RequestMethod.PUT, value = "/statistics/{accountName}", consumes = MediaType.APPLICATION_JSON_VALUE)
	void updateStatistics(@PathVariable("accountName") String accountName, Account account);

}
```

- Everything you need is just an interface
- You can share the `@RequestMapping` part between the Spring MVC controller and Feign methods
- The example above specifies just a desired service id — `statistics-service` — thanks to auto-discovery through Eureka
- The `fallback` provides a Resilience4j circuit-breaker fallback when the target is unavailable

### Monitoring

> **Before → after:** the legacy project pushed **Hystrix** metrics to **Turbine**
> (via Spring Cloud Bus / AMQP) and rendered them in the **Hystrix Dashboard**. Both
> the `monitoring` and `turbine-stream-service` modules have been **deleted**.
> Observability now uses **Actuator + Micrometer**: each service exposes metrics at
> `/actuator/prometheus`, scraped by **Prometheus** and visualized in **Grafana**.

Each service publishes JVM, HTTP and Resilience4j circuit-breaker metrics via Micrometer at `/actuator/prometheus`. Prometheus and Grafana are wired into the development compose file (`docker-compose.dev.yml`), including a prebuilt "PiggyMetrics — Resilience4j & JVM" Grafana dashboard. See [`docs/phase-4-smoke-checklist.md`](docs/phase-4-smoke-checklist.md) for the observability smoke steps.

### Log analysis

Centralized logging can be very useful while attempting to identify problems in a distributed environment. Elasticsearch, Logstash and Kibana stack lets you search and analyze your logs, utilization and network activity data with ease.

### Distributed tracing

Analyzing problems in distributed systems can be difficult, especially trying to trace requests that propagate from one microservice to another.

> **Before → after:** the legacy project used **Spring Cloud Sleuth**, which is
> removed in Spring Boot 3. Tracing is now provided by **[Micrometer Tracing](https://docs.micrometer.io/tracing/reference/)**
> (with an OpenTelemetry/Zipkin bridge).

Micrometer Tracing adds two types of IDs to the logging: `traceId` and `spanId`. A `spanId` represents a basic unit of work, for example sending an HTTP request. The `traceId` contains a set of spans forming a tree-like structure. Using `traceId` and `spanId` for each operation, we know when and where our application is as it processes a request, making reading logs much easier.

The logs are as follows — notice the `[appname,traceId,spanId]` entries from the Slf4J MDC:

```text
2026-07-05 23:13:49.381  INFO [gateway,3216d0de1384bb4f,3216d0de1384bb4f] 2999 --- [ctor-http-nio-2] o.s.c.g.h.RoutePredicateHandlerMapping   : Route matched: account-service
2026-07-05 23:13:49.562  INFO [account-service,3216d0de1384bb4f,404ff09c5cf91d2e] 3079 --- [nio-6000-exec-1] c.p.account.service.AccountServiceImpl   : new account has been created: test
```

- *`appname`*: The name of the application that logged the span from the property `spring.application.name`
- *`traceId`*: This is an ID that is assigned to a single request, job, or action
- *`spanId`*: The ID of a specific operation that took place

## Infrastructure automation

Deploying microservices, with their interdependence, is much more complex process than deploying a monolithic application. It is really important to have a fully automated infrastructure. We can achieve following benefits with Continuous Delivery approach:

- The ability to release software anytime
- Any build could end up being a release
- Build artifacts once - deploy as needed

Here is a simple Continuous Delivery workflow, implemented in this project:

<img width="880" src="https://cloud.githubusercontent.com/assets/6069066/14159789/0dd7a7ce-f6e9-11e5-9fbb-a7fe0f4431e3.png">

In this [configuration](.github/workflows/ci.yml), **GitHub Actions** (replacing the legacy Travis CI) builds and tests every microservice on **JDK 21** on each push and pull request, running the Testcontainers-based integration tests and publishing the JaCoCo coverage report as a workflow artifact. The workflow defines a single job, `build-java-21`; enforcing it as a required status check is a manual branch-protection step (see [`MODERNIZATION_PLAN.md`](MODERNIZATION_PLAN.md), §9). Automated Docker image builds/pushes are planned as part of the modernization effort (see [`MODERNIZATION_PLAN.md`](MODERNIZATION_PLAN.md), Phase 6 — not yet shipped).

## Let's try it out

> **Build prerequisite:** from the modernization onward the build requires **JDK 21**
> (Spring Boot 3.3 needs 17+). The reactor is **7 modules**; the `monitoring` and
> `turbine-stream-service` modules were removed.

Note that starting the Spring Boot applications, the MongoDB instances and RabbitMQ require at least 4 GB of RAM.

#### Before you start
- Install Docker and Docker Compose.
- Change the environment variable values in the `.env` file for more security or leave them as they are.
- Build the project: `mvn package [-DskipTests]` (with `JAVA_HOME` pointing at a JDK 21).

#### Development mode (recommended)
Clone the repository, build the artifacts with Maven, then run:

```
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up --build
```

`docker-compose.dev.yml` extends `docker-compose.yml` — it builds the modernized images locally from source (including the rewritten gateway and auth-service), exposes all container ports for convenient development, and adds the Prometheus + Grafana observability stack.

> **Note on production mode:** `docker-compose.yml` on its own still references the
> upstream `sqshq/piggymetrics-*` images on Docker Hub as a behavioral oracle.
> Publishing modernized images is Phase 6 (not yet shipped), so use the **development
> mode** command above to run this fork's modernized code.

To run the Testcontainers-based tests locally you need a running Docker daemon. On Docker Desktop 29+ (Apple Silicon), set `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock` and run `mvn verify -Dapi.version=1.44`; GitHub-hosted CI needs neither.

If you'd like to start the applications in IntelliJ IDEA you need to either use the [EnvFile plugin](https://plugins.jetbrains.com/plugin/7861-envfile) or manually export the environment variables listed in the `.env` file (verify they were exported: `printenv`).

#### Important endpoints
- http://localhost:80 — Gateway (**Spring Cloud Gateway**; replaces Netflix Zuul)
- http://localhost:8761 — Eureka Dashboard
- http://localhost:9090 — Prometheus (dev compose; replaces the removed Hystrix Dashboard/Turbine)
- http://localhost:3000 — Grafana (dev compose; default login/password: admin/admin)
- http://localhost:15672 — RabbitMQ management (default login/password: guest/guest)

> **Modernization note:** the Hystrix Dashboard + Turbine monitoring stack has been
> removed. Circuit-breaker and JVM metrics are now exposed via Micrometer at each
> service's `/actuator/prometheus`, scraped by Prometheus and visualized in Grafana.
> See [`docs/phase-4-smoke-checklist.md`](docs/phase-4-smoke-checklist.md).

## Contributions are welcome!

PiggyMetrics is open source, and would greatly appreciate your help. Feel free to suggest and implement any improvements.
