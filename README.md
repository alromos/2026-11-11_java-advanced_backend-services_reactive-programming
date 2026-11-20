# Reactive Programming

Spring Boot (WebFlux, R2DBC) application.

## Local startup

Run the application locally via `TestApplication`
(`src/test/java/by/alromos/java/advanced/reactive/TestApplication.java`):

```
./gradlew bootTestRun
```

`TestApplication` starts the regular `Application` together with `TestcontainersConfiguration`, which
provides the infrastructure as Docker containers:

- PostgreSQL (`postgres:17`), wired in automatically via `@ServiceConnection`
- WireMock, which stubs the external sports API (its base URL is injected as a dynamic property)

Docker must be running. No manual database or API setup is needed.

Note: `./gradlew bootRun` starts only `Application` and expects the database and the sports API to be
available on its own.
