package by.alromos.java.advanced.reactive.repository;

import by.alromos.java.advanced.reactive.model.Sport;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface SportRepository extends ReactiveCrudRepository<Sport, Integer> {

  Flux<Sport> findByNameContainingIgnoreCaseOrderByName(String name);

}
