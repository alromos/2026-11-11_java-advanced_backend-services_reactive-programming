package by.alromos.java.advanced.reactive.setup;

import by.alromos.java.advanced.reactive.model.Sport;
import by.alromos.java.advanced.reactive.repository.SportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SetupTest {

  private SportRepository repository;

  private static WebClient webClientReturning(HttpStatus status, String body) {
    return WebClient.builder()
        .exchangeFunction(request -> Mono.just(ClientResponse.create(status)
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .body(body)
            .build()))
        .build();
  }

  private static WebClient webClientCounting(AtomicInteger attempts, HttpStatus status, String body) {
    return WebClient.builder()
        .exchangeFunction(request -> {
          attempts.incrementAndGet();
          return Mono.just(ClientResponse.create(status)
              .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
              .body(body)
              .build());
        })
        .build();
  }

  @BeforeEach
  void setUp() {
    repository = mock(SportRepository.class);
    when(repository.save(any(Sport.class)))
        .thenAnswer(invocation -> Mono.just(new Sport(1, ((Sport) invocation.getArgument(0)).name())));
  }

  @Test
  void savesEachSportOneByOneInOrder() {
    Setup setup = new Setup(webClientReturning(HttpStatus.OK, "[\"Football\",\"Tennis\"]"), repository);

    StepVerifier.create(setup.load()).verifyComplete();

    InOrder order = inOrder(repository);
    order.verify(repository).save(new Sport(null, "Football"));
    order.verify(repository).save(new Sport(null, "Tennis"));
    verify(repository, never()).saveAll(any(Iterable.class));
  }

  @Test
  void doesNotStartNextSaveUntilPreviousOneCompletes() {
    Sinks.One<Sport> firstSave = Sinks.one();
    when(repository.save(new Sport(null, "Football"))).thenReturn(firstSave.asMono());
    Setup setup = new Setup(webClientReturning(HttpStatus.OK, "[\"Football\",\"Tennis\"]"), repository);

    StepVerifier.create(setup.load())
        .then(() -> verify(repository, never()).save(new Sport(null, "Tennis")))
        .then(() -> firstSave.tryEmitValue(new Sport(1, "Football")))
        .verifyComplete();

    verify(repository).save(new Sport(null, "Tennis"));
  }

  @Test
  void skipsDuplicateAndContinues() {
    when(repository.save(new Sport(null, "Tennis"))).thenReturn(Mono.error(new DuplicateKeyException("dup")));
    Setup setup = new Setup(webClientReturning(HttpStatus.OK, "[\"Football\",\"Tennis\",\"Hockey\"]"), repository);

    StepVerifier.create(setup.load()).verifyComplete();

    verify(repository).save(new Sport(null, "Football"));
    verify(repository).save(new Sport(null, "Tennis"));
    verify(repository).save(new Sport(null, "Hockey"));
  }

  @Test
  void completesOnEmptyArray() {
    Setup setup = new Setup(webClientReturning(HttpStatus.OK, "[]"), repository);

    StepVerifier.create(setup.load()).verifyComplete();

    verifyNoInteractions(repository);
  }

  @Test
  void propagatesOtherRepositoryErrors() {
    when(repository.save(any(Sport.class))).thenReturn(Mono.error(new IllegalStateException("boom")));
    Setup setup = new Setup(webClientReturning(HttpStatus.OK, "[\"Football\"]"), repository);

    StepVerifier.create(setup.load()).expectError(IllegalStateException.class).verify();
  }

  @Test
  void retriesThreeTimesOnServerErrorThenFailsWithClearError() {
    AtomicInteger attempts = new AtomicInteger();
    Setup setup = new Setup(webClientCounting(attempts, HttpStatus.INTERNAL_SERVER_ERROR, "oops"), repository);

    StepVerifier.withVirtualTime(setup::load)
        .thenAwait(Duration.ofMinutes(1))
        .expectErrorSatisfies(e -> assertThat(e)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to load sports from GET /sports")
            .hasCauseInstanceOf(WebClientResponseException.class))
        .verify();

    assertThat(attempts).hasValue(4);
    verifyNoInteractions(repository);
  }

  @Test
  void retriesWhenEndpointIsUnavailableThenFailsWithClearError() {
    AtomicInteger attempts = new AtomicInteger();
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> {
          attempts.incrementAndGet();
          return Mono.error(new WebClientRequestException(
              new ConnectException("refused"), HttpMethod.GET, request.url(), request.headers()));
        })
        .build();
    Setup setup = new Setup(webClient, repository);

    StepVerifier.withVirtualTime(setup::load)
        .thenAwait(Duration.ofMinutes(1))
        .expectErrorSatisfies(e -> assertThat(e)
            .isInstanceOf(IllegalStateException.class)
            .hasCauseInstanceOf(WebClientRequestException.class))
        .verify();

    assertThat(attempts).hasValue(4);
    verifyNoInteractions(repository);
  }

  @Test
  void succeedsWhenEndpointRecoversWithinRetries() {
    AtomicInteger attempts = new AtomicInteger();
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.just(attempts.incrementAndGet() < 3
            ? ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build()
            : ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("[\"Football\"]")
                .build()))
        .build();
    Setup setup = new Setup(webClient, repository);

    StepVerifier.withVirtualTime(setup::load)
        .thenAwait(Duration.ofMinutes(1))
        .verifyComplete();

    assertThat(attempts).hasValue(3);
    verify(repository).save(new Sport(null, "Football"));
  }

  @Test
  void doesNotRetryClientError() {
    AtomicInteger attempts = new AtomicInteger();
    Setup setup = new Setup(webClientCounting(attempts, HttpStatus.NOT_FOUND, "nope"), repository);

    StepVerifier.create(setup.load())
        .expectErrorSatisfies(e -> assertThat(e)
            .isInstanceOf(IllegalStateException.class)
            .hasCauseInstanceOf(WebClientResponseException.NotFound.class))
        .verify();

    assertThat(attempts).hasValue(1);
    verifyNoInteractions(repository);
  }

  @Test
  void failsWithClearErrorOnMalformedBodyWithoutRetry() {
    AtomicInteger attempts = new AtomicInteger();
    Setup setup = new Setup(webClientCounting(attempts, HttpStatus.OK, "{\"not\":\"an array\"}"), repository);

    StepVerifier.create(setup.load())
        .expectErrorSatisfies(e -> assertThat(e)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to load sports from GET /sports"))
        .verify();

    assertThat(attempts).hasValue(1);
    verifyNoInteractions(repository);
  }

  @Test
  void failsWithClearErrorOnNoContentResponse() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build()))
        .build();
    Setup setup = new Setup(webClient, repository);

    StepVerifier.create(setup.load())
        .expectErrorSatisfies(e -> assertThat(e)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("response body is empty"))
        .verify();

    verifyNoInteractions(repository);
  }

}
