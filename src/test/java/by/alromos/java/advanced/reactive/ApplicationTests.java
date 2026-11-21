package by.alromos.java.advanced.reactive;

import by.alromos.java.advanced.reactive.model.Sport;
import by.alromos.java.advanced.reactive.repository.SportRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationTests extends BaseIntegrationTest {

  @Autowired
  private SportRepository repository;

  @Test
  void loadsSportsFromWireMockIntoPostgresOnStartup() {
    StepVerifier.create(repository.findAll().map(Sport::name).collectList())
        .assertNext(names -> assertThat(names)
            .containsExactlyInAnyOrderElementsOf(List.of("Football", "Basketball", "Tennis", "Hockey", "Volleyball")))
        .verifyComplete();
  }

}
