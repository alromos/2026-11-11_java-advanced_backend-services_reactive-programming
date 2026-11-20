package by.alromos.java.advanced.reactive.repository;

import by.alromos.java.advanced.reactive.model.Sport;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface SportRepository extends ReactiveCrudRepository<Sport, Integer> {

}
