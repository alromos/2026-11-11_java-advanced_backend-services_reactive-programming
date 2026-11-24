package by.alromos.java.advanced.reactive.setup;

import by.alromos.java.advanced.reactive.model.Sport;
import by.alromos.java.advanced.reactive.repository.SportRepository;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.util.retry.Retry;

import java.time.Duration;

@Component
public class Setup implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(Setup.class);
  private static final String SPORTS_URI = "/sports";
  private static final int MAX_RETRIES = 3;
  private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);
  private static final int BATCH_SIZE = 2;

  private final WebClient webClient;
  private final SportRepository repository;

  public Setup(WebClient webClient, SportRepository repository) {
    this.webClient = webClient;
    this.repository = repository;
  }

  @Override
  public void run(ApplicationArguments args) {
    load().block();
  }

  Mono<Void> load() {
    Flux<Sport> sports = fetchSportNames()
        .flatMapMany(Flux::fromArray)
        .map(name -> new Sport(null, name))
        .concatMap(this::save, 1);

    return Mono.create(sink -> sports.subscribe(new BatchSubscriber(BATCH_SIZE, sink)));
  }

  private Mono<String[]> fetchSportNames() {
    return webClient.get()
        .uri(SPORTS_URI)
        .retrieve()
        .bodyToMono(String[].class)
        .switchIfEmpty(Mono.error(new IllegalStateException("response body is empty")))
        .retryWhen(Retry.backoff(MAX_RETRIES, INITIAL_BACKOFF)
            .filter(Setup::errorIsTransient)
            .doBeforeRetry(signal -> log.warn("Sports API call failed (retry {}/{}): {}",
                signal.totalRetries() + 1, MAX_RETRIES, signal.failure().toString()))
            .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
        .onErrorMap(e -> new IllegalStateException(
            "Failed to load sports from GET " + SPORTS_URI + ": " + e.getMessage(), e));
  }

  private static boolean errorIsTransient(Throwable throwable) {
    return throwable instanceof WebClientRequestException
        || (throwable instanceof WebClientResponseException e && e.getStatusCode().is5xxServerError());
  }

  private Mono<Sport> save(Sport sport) {
    return repository.save(sport)
        .doOnNext(saved -> log.info("Saved sport: id={}, name={}", saved.id(), saved.name()))
        .onErrorResume(DuplicateKeyException.class, e -> {
          log.warn("Sport '{}' already exists, skipping", sport.name());
          return Mono.empty();
        });
  }

  private static final class BatchSubscriber extends BaseSubscriber<Sport> {

    private final int batchSize;
    private final MonoSink<Void> sink;
    private int receivedInBatch;

    BatchSubscriber(int batchSize, MonoSink<Void> sink) {
      this.batchSize = batchSize;
      this.sink = sink;
    }

    @Override
    protected void hookOnSubscribe(Subscription subscription) {
      log.info("onSubscribe: request({})", batchSize);
      subscription.request(batchSize);
    }

    @Override
    protected void hookOnNext(Sport sport) {
      receivedInBatch++;
      log.info("onNext ({}/{}): {}", receivedInBatch, batchSize, sport.name());
      if (receivedInBatch == batchSize) {
        receivedInBatch = 0;
        log.info("Batch consumed: request({})", batchSize);
        request(batchSize);
      }
    }

    @Override
    protected void hookOnComplete() {
      log.info("onComplete");
      sink.success();
    }

    @Override
    protected void hookOnError(Throwable throwable) {
      log.error("onError", throwable);
      sink.error(throwable);
    }

  }

}
