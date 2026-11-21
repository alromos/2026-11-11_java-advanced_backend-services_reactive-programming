package by.alromos.java.advanced.reactive.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("sport")
public record Sport(@Id Integer id, String name) {

}
