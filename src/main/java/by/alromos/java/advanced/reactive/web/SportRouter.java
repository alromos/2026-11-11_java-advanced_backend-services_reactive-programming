package by.alromos.java.advanced.reactive.web;

import by.alromos.java.advanced.reactive.model.Sport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class SportRouter {

  @Bean
  @RouterOperations({
      @RouterOperation(
          path = "/api/v1/sport/{sportName}",
          method = RequestMethod.POST,
          beanClass = SportHandler.class,
          beanMethod = "create",
          consumes = MediaType.APPLICATION_JSON_VALUE,
          operation = @Operation(
              operationId = "createSport",
              summary = "Create a sport",
              parameters = @Parameter(name = "sportName", in = ParameterIn.PATH, required = true,
                  description = "Name of the sport to create"),
              responses = {
                  @ApiResponse(responseCode = "201", description = "Sport created",
                      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                          schema = @Schema(implementation = Sport.class))),
                  @ApiResponse(responseCode = "400", description = "Sport name is blank",
                      content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                          schema = @Schema(implementation = ProblemDetail.class))),
                  @ApiResponse(responseCode = "409", description = "Sport already exists",
                      content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                          schema = @Schema(implementation = ProblemDetail.class))),
                  @ApiResponse(responseCode = "415", description = "Unsupported content type",
                      content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                          schema = @Schema(implementation = ProblemDetail.class)))
              })),
      @RouterOperation(
          path = "/api/v1/sport",
          method = RequestMethod.GET,
          beanClass = SportHandler.class,
          beanMethod = "search",
          operation = @Operation(
              operationId = "searchSports",
              summary = "Search sports by name",
              parameters = @Parameter(name = "q", in = ParameterIn.QUERY,
                  description = "Case-insensitive part of the sport name; all sports are returned when omitted"),
              responses = @ApiResponse(responseCode = "200", description = "Matching sports ordered by name",
                  content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                      array = @ArraySchema(schema = @Schema(implementation = Sport.class)))))),
  })
  public RouterFunction<ServerResponse> sportRoutes(SportHandler handler) {
    return RouterFunctions.route()
        .path("/api/v1/sport", builder -> builder
            .POST("/{sportName}", handler::create)
            .GET(handler::search))
        .build();
  }

}
