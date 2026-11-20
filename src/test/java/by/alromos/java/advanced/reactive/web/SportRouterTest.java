package by.alromos.java.advanced.reactive.web;

import by.alromos.java.advanced.reactive.model.Sport;
import by.alromos.java.advanced.reactive.repository.SportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SportRouterTest {

  private SportRepository repository;
  private WebTestClient client;

  @BeforeEach
  void setUp() {
    repository = mock(SportRepository.class);
    client = WebTestClient
        .bindToRouterFunction(new SportRouter().sportRoutes(new SportHandler(repository)))
        .build();
  }

  @Test
  void createsSport() {
    when(repository.save(new Sport(null, "Rugby"))).thenReturn(Mono.just(new Sport(7, "Rugby")));

    client.post().uri("/api/v1/sport/{name}", "Rugby")
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").isEqualTo(7)
        .jsonPath("$.name").isEqualTo("Rugby");
  }

  @Test
  void returnsConflictWhenSportAlreadyExists() {
    when(repository.save(any(Sport.class))).thenReturn(Mono.error(new DuplicateKeyException("dup")));

    client.post().uri("/api/v1/sport/{name}", "Football")
        .exchange()
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.status").isEqualTo(409)
        .jsonPath("$.detail").isEqualTo("Sport 'Football' already exists");
  }

  @Test
  void rejectsBlankName() {
    client.post().uri("/api/v1/sport/{name}", " ")
        .exchange()
        .expectStatus().isBadRequest();

    verifyNoInteractions(repository);
  }

  @Test
  void createAcceptsApplicationJsonContentType() {
    when(repository.save(new Sport(null, "Rugby"))).thenReturn(Mono.just(new Sport(7, "Rugby")));

    client.post().uri("/api/v1/sport/{name}", "Rugby")
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isCreated();
  }

  @Test
  void createAcceptsApplicationJsonContentTypeWithCharset() {
    when(repository.save(new Sport(null, "Rugby"))).thenReturn(Mono.just(new Sport(7, "Rugby")));

    client.post().uri("/api/v1/sport/{name}", "Rugby")
        .header(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8")
        .exchange()
        .expectStatus().isCreated();
  }

  @ParameterizedTest
  @ValueSource(strings = {"text/plain", "application/xml", "application/x-www-form-urlencoded", "*/*", "not a media type"})
  void createRejectsNonJsonContentType(String contentType) {
    client.post().uri("/api/v1/sport/{name}", "Rugby")
        .header(HttpHeaders.CONTENT_TYPE, contentType)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .expectBody()
        .jsonPath("$.status").isEqualTo(415)
        .jsonPath("$.detail").doesNotExist();

    verifyNoInteractions(repository);
  }

  @Test
  void doesNotSwallowOtherErrors() {
    when(repository.save(any(Sport.class))).thenReturn(Mono.error(new IllegalStateException("boom")));

    client.post().uri("/api/v1/sport/{name}", "Rugby")
        .exchange()
        .expectStatus().is5xxServerError();
  }

  @Test
  void searchesSportsByName() {
    when(repository.findByNameContainingIgnoreCaseOrderByName("ball"))
        .thenReturn(Flux.just(new Sport(1, "Football"), new Sport(2, "Volleyball")));

    client.get().uri("/api/v1/sport?q=ball")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(2)
        .jsonPath("$[0].name").isEqualTo("Football")
        .jsonPath("$[1].name").isEqualTo("Volleyball");
  }

  @Test
  void searchWithoutQueryMatchesEverything() {
    when(repository.findByNameContainingIgnoreCaseOrderByName(""))
        .thenReturn(Flux.just(new Sport(1, "Football")));

    client.get().uri("/api/v1/sport")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1);
  }

}
