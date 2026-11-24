package by.alromos.java.advanced.reactive.repository;

import by.alromos.java.advanced.reactive.BaseIntegrationTest;
import by.alromos.java.advanced.reactive.model.Sport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import reactor.test.StepVerifier;

class SportRepositoryTest extends BaseIntegrationTest {

  @Autowired
  private SportRepository repository;

  @Test
  void rejectsDuplicateNameOfSportLoadedOnStartup() {
    StepVerifier.create(repository.save(new Sport(null, "Football")))
        .expectError(DuplicateKeyException.class)
        .verify();
  }

  @Test
  void findsByNameSubstringIgnoringCaseSortedByName() {
    StepVerifier.create(repository.findByNameContainingIgnoreCaseOrderByName("BALL").map(Sport::name))
        .expectNext("Basketball", "Football", "Volleyball")
        .verifyComplete();
  }

  @Test
  void findsNothingWhenNoNameMatches() {
    StepVerifier.create(repository.findByNameContainingIgnoreCaseOrderByName("no-such-sport-xyz"))
        .verifyComplete();
  }

}
