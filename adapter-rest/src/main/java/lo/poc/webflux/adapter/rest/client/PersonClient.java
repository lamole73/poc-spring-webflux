package lo.poc.webflux.adapter.rest.client;

import lo.poc.webflux.adapter.rest.client.domain.Person;
import lo.poc.webflux.adapter.rest.controller.domain.PersonVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonClient {

    private final WebClient.Builder builder;

    @Value("${pocconfig.personclient.baseUrl}")
    private String baseUrl;

    public Mono<PersonVo> retrieveSlowEndpoint(Long id) {
        WebClient webClient = builder.baseUrl(baseUrl)
//                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();

        return webClient.get()
                .uri("/persons/{id}/servicesync", id)
                .retrieve()
                .bodyToMono(PersonVo.class)
                .log();
    }

    public Mono<PersonVo> retrieveViaAction(Long id, String action) {
        WebClient webClient = builder.baseUrl(baseUrl)
//                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();

        return webClient.get()
                .uri("/persons/{id}/{action}", id, action)
                .retrieve()
                .bodyToMono(PersonVo.class)
                .log();
    }

    public Mono<Object> retrieveViaEndpoint(String endpoint) {
        WebClient webClient = builder.baseUrl(baseUrl)
//                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();

        return webClient.get()
                .uri("/persons" + endpoint)
                .retrieve()
                .bodyToMono(Object.class)
                .log();
    }

    public static void main(String[] args) throws InterruptedException {

        // Concurent number of calls
        int concurrent = 1;

        // Resources/Applications to run onto
        List<Tuple2<String, String>> list = Arrays.asList(
                Tuples.of("WebFlux", "http://localhost:8082"),
                Tuples.of("WebMVC", "http://localhost:8081")
        );

        // Endpoints to access
        List<String> endpoints = Arrays.asList(
                "/{id}/service",
                "/{id}/serviceblock",
                "/{id}/servicesync",
                "/{id}/client",
                "/{id}/clientsync"
        );

        PersonClient personClient = new PersonClient(WebClient.builder());

        long startAll = System.currentTimeMillis();
        log.info(">>>>>>>>>>>> Start All");
        list.forEach(s -> {
            long startService = System.currentTimeMillis();
            personClient.baseUrl = s.getT2();
            log.info(">>>>>>>> Start for {}, on {}", s.getT1(), s.getT2());

            for (String endpoint : endpoints) {
                // Construct what to run
                List<Mono<Object>> calls = IntStream.range(0, concurrent)
                        .mapToObj(i ->
                                personClient.retrieveViaEndpoint(endpoint.replace("{id}", "" + i))
                                        .onErrorResume(e -> Mono.just("Error: " + e.getMessage()))
                                        .log()
                        ).collect(Collectors.toList());

                // Run
                long start = System.currentTimeMillis();
                log.info(">>>> Running for {}, endpoint {}, on {}", s.getT1(), endpoint, s.getT2());
//                log.info(">>>>>>>>>> Before Zipping...");
                List<Object> results = Mono.zip(calls, res -> {
//                    log.info(">>>>>>>>>> Zipping...");
//                    Arrays.asList(arr).forEach(e -> log.info(">>>>>>>>>> Got {}", e));

                    // Check if we had an error
                    Optional<Object> firstError = Arrays.stream(res).filter(r -> r instanceof String).findFirst();
                    firstError.ifPresent(e -> log.error(">>>> ERROR: {}", e));
                    return Arrays.asList(res);
                }).block();
//                        .block(Duration.ofSeconds(10));
                log.info(">>>> Finished {} calls for {}, endpoint {}... on {} ms", results.size(), s.getT1(), endpoint, (System.currentTimeMillis() - start));
            }
            log.info(">>>>>>>> End for {} on {} ms", s.getT1(), (System.currentTimeMillis() - startService));
        });
        log.info(">>>>>>>>>>>> End ALL on {} ms", (System.currentTimeMillis() - startAll));


    }

}
