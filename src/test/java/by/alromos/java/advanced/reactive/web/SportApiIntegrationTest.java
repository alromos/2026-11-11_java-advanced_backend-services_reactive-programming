package by.alromos.java.advanced.reactive.web;

import by.alromos.java.advanced.reactive.BaseIntegrationTest;
import by.alromos.java.advanced.reactive.repository.SportRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

class SportApiIntegrationTest extends BaseIntegrationTest {

  private static final String TEST_PREFIX = "zz-it-";

  @Autowired
  private ApplicationContext context;

  @Autowired
  private SportRepository repository;

  private WebTestClient client;

  @BeforeEach
  void setUp() {
    client = WebTestClient.bindToApplicationContext(context).build();
  }

  @AfterEach
  void cleanUp() {
    repository.findByNameContainingIgnoreCaseOrderByName(TEST_PREFIX)
        .collectList()
        .flatMap(repository::deleteAll)
        .block();
  }

  @Test
  void createdSportIsPersistedAndFoundBySearch() {
    client.post().uri("/api/v1/sport/{name}", TEST_PREFIX + "Rugby")
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").isNumber()
        .jsonPath("$.name").isEqualTo(TEST_PREFIX + "Rugby");

    client.get().uri("/api/v1/sport?q={q}", TEST_PREFIX)
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[0].name").isEqualTo(TEST_PREFIX + "Rugby");
  }

  @Test
  void returnsConflictWhenCreatingSportTwice() {
    client.post().uri("/api/v1/sport/{name}", TEST_PREFIX + "Curling")
        .exchange()
        .expectStatus().isCreated();

    client.post().uri("/api/v1/sport/{name}", TEST_PREFIX + "Curling")
        .exchange()
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.status").isEqualTo(409)
        .jsonPath("$.detail").isEqualTo("Sport '" + TEST_PREFIX + "Curling' already exists");
  }

  @Test
  void returnsConflictForSportLoadedOnStartup() {
    client.post().uri("/api/v1/sport/{name}", "Football")
        .exchange()
        .expectStatus().isEqualTo(409);
  }

  @Test
  void rejectsBlankName() {
    client.post().uri("/api/v1/sport/{name}", " ")
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  void searchIsCaseInsensitiveAndSortedByName() {
    client.get().uri("/api/v1/sport?q=BALL")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(3)
        .jsonPath("$[0].name").isEqualTo("Basketball")
        .jsonPath("$[1].name").isEqualTo("Football")
        .jsonPath("$[2].name").isEqualTo("Volleyball");
  }

  @Test
  void searchWithoutMatchesReturnsEmptyArray() {
    client.get().uri("/api/v1/sport?q=no-such-sport-xyz")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .json("[]");
  }

  @Test
  void searchWithoutQueryReturnsAllLoadedSports() {
    client.get().uri("/api/v1/sport")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(5);
  }

}
