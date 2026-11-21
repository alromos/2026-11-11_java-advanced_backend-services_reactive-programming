package by.alromos.java.advanced.reactive;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  private static final int WIREMOCK_PORT = 8080;

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgres() {
    return new PostgreSQLContainer("postgres:17");
  }

  @Bean
  GenericContainer<?> wiremock() {
    return new GenericContainer<>("wiremock/wiremock")
        .withExposedPorts(WIREMOCK_PORT)
        .withCopyFileToContainer(
            MountableFile.forHostPath("wiremock/mappings/sports.json"),
            "/home/wiremock/mappings/sports.json")
        .waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200));
  }

  @Bean
  DynamicPropertyRegistrar sportsApiBaseUrl(GenericContainer<?> wiremock) {
    return registry -> registry.add("sports.api.base-url",
        () -> "http://%s:%d".formatted(wiremock.getHost(), wiremock.getMappedPort(WIREMOCK_PORT)));
  }

}
