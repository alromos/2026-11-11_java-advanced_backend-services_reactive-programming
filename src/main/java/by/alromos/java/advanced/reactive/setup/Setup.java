package by.alromos.java.advanced.reactive.setup;

import by.alromos.java.advanced.reactive.model.Sport;
import by.alromos.java.advanced.reactive.repository.SportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class Setup implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(Setup.class);

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
    return webClient.get()
        .uri("/sports")
        .retrieve()
        .bodyToMono(String[].class)
        .flatMapMany(Flux::fromArray)
        .map(name -> new Sport(null, name))
        .concatMap(this::save)
        .then();
  }

  private Mono<Sport> save(Sport sport) {
    return repository.save(sport)
        .doOnNext(saved -> log.info("Saved sport: id={}, name={}", saved.id(), saved.name()))
        .onErrorResume(DuplicateKeyException.class, e -> {
          log.warn("Sport '{}' already exists, skipping", sport.name());
          return Mono.empty();
        });
  }

}
