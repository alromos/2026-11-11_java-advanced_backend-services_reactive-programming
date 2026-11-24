package by.alromos.java.advanced.reactive.web;

import by.alromos.java.advanced.reactive.model.Sport;
import by.alromos.java.advanced.reactive.repository.SportRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class SportHandler {

  private final SportRepository repository;

  public SportHandler(SportRepository repository) {
    this.repository = repository;
  }

  private static Mono<ServerResponse> problem(HttpStatus status) {
    return ServerResponse.status(status).bodyValue(ProblemDetail.forStatus(status));
  }

  private static Mono<ServerResponse> problem(HttpStatus status, String detail) {
    return ServerResponse.status(status).bodyValue(ProblemDetail.forStatusAndDetail(status, detail));
  }

  private static boolean hasUnsupportedContentType(ServerRequest request) {
    try {
      return request.headers().contentType()
          .filter(contentType -> !MediaType.APPLICATION_JSON.equalsTypeAndSubtype(contentType))
          .isPresent();
    } catch (InvalidMediaTypeException e) {
      return true;
    }
  }

  public Mono<ServerResponse> create(ServerRequest request) {
    if (hasUnsupportedContentType(request)) {
      return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    String name = request.pathVariable("sportName");

    if (name.isBlank()) {
      return problem(HttpStatus.BAD_REQUEST, "Sport name must not be blank");
    }

    return repository.save(new Sport(null, name))
        .flatMap(saved -> ServerResponse.status(HttpStatus.CREATED).bodyValue(saved))
        .onErrorResume(DuplicateKeyException.class,
            e -> problem(HttpStatus.CONFLICT, "Sport '%s' already exists".formatted(name)));
  }

  public Mono<ServerResponse> search(ServerRequest request) {
    String query = request.queryParam("q")
        .orElse("");

    return ServerResponse.ok()
        .body(repository.findByNameContainingIgnoreCaseOrderByName(query), Sport.class);
  }

}
