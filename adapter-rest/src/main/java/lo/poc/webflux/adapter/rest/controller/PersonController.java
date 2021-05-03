package lo.poc.webflux.adapter.rest.controller;

import lo.poc.webflux.adapter.rest.client.PersonClient;
import lo.poc.webflux.adapter.rest.controller.domain.PersonVo;
import lo.poc.webflux.adapter.rest.service.PersonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping(path = "/persons")
@Slf4j
@RequiredArgsConstructor
public class PersonController {

    @Value("${pocconfig.personcontroller.parallelSearchMaxThreads}")
    private Integer parallelSearchMaxThreads = 10;

    final PersonService personService;

    final PersonClient personClient;

    /**
     * Return a Mono via async via Mono.fromCallable with simulated delay on callable call
     * This results to full async operation end-to-end, Thread unblocked.
     * Reason being that personService returns immediately and the actual response is returned by the onNext from Mono after the delay
     */
    @GetMapping(path = "/{id}/service")
    Mono<PersonVo> retrieveService(@PathVariable("id") Long id) {
        log.info("retrieveService {}", id);
        Mono<PersonVo> entity = personService.retrieveAsync(id);
        log.info("retrieveService {}, result {}", id, entity);
        return entity.log();
    }

    /**
     * Return a Mono via async with simulated delay on creating the Mono on service
     * This results to sync operation, Thread blocked.
     * Reason being that personService does not return immediately.
     */
    @GetMapping(path = "/{id}/serviceblock")
    Mono<PersonVo> retrieveServiceAsyncBlock(@PathVariable("id") Long id) {
        log.info("retrieveServiceAsyncBlock {}", id);
        Mono<PersonVo> entity = personService.retrieveAsyncBlocking(id);
        log.info("retrieveServiceAsyncBlock {}, result {}", id, entity);
        return entity.log();
    }

    /**
     * Return a VO via sync with simulated delay on returning the response from service.
     * This results to sync operation, Thread blocked.
     * Reason being that personService does not return immediately.
     */
    @GetMapping(path = "/{id}/servicesync")
    PersonVo retrieveServiceSync(@PathVariable("id") Long id) {
        log.info("retrieveServiceSync {}", id);
        PersonVo entity = personService.retrieveSync(id);
        log.info("retrieveServiceSync {}, result {}", id, entity);
        return entity;
    }

    /**
     * Return a Mono via WebClient which calls a slow running endpoint.
     * This results to full async operation end-to-end, Thread unblocked.
     * Reason being that personClient returns immediately and the actual response is returned by the onNext from the WebClient after the slow running endpoint responds.
     */
    @GetMapping(path = "/{id}/client")
    Mono<PersonVo> retrieveViaClient(@PathVariable("id") Long id) {
        log.info("retrieveViaClient {}", id);
        Mono<PersonVo> entity = personClient.retrieveSlowEndpoint(id); //.map(PersonMapper.INSTANCE::map);
        log.info("retrieveViaClient {}, result {}", id, entity);
        return entity.log();
    }

    /**
     * Return a VO via blocking the WebClient which calls a slow running endpoint.
     * This results to sync operation end-to-end, Thread blocked.
     * Reason being that personClient returns immediately but we block until we receive the actual response from the WebClient after the slow running endpoint responds.
     */
    @GetMapping(path = "/{id}/clientsync")
    PersonVo retrieveViaClientSync(@PathVariable("id") Long id) {
        log.info("retrieveViaClientSync {}", id);
        Mono<PersonVo> entity = personClient.retrieveSlowEndpoint(id); //.map(PersonMapper.INSTANCE::map);
        PersonVo res = entity.log().block();
        log.info("retrieveViaClientSync {}, result {}", id, res);
        return res;
    }

    /**
     * Return a Mono via parallel calls (max 10 in parallel) to WebClient which calls a slow running endpoint.
     * Note: In this case we use Flux.flatMap with concurrency (10) so that the slow calls are executed 10 at a time.
     * This results to full async operation end-to-end, Thread unblocked.
     * Reason being that <code>Mono<List></code> returns immediately and the actual response is returned by the onNext from the WebClient calls after the slow running endpoint responds.
     */
    @GetMapping(path = "/client")
    Mono<List<PersonVo>> searchViaClient(@RequestBody List<Long> ids) {
        log.info("searchViaClient {}", ids);

//        // The below runs immediately all in parallel
//        Mono<List<PersonVo>> result = Flux.fromIterable(ids)
//                .parallel()
//                .runOn(Schedulers.newBoundedElastic(parallelSearchMaxThreads, parallelSearchMaxThreads*10000, "searchViaClient"))
//                .flatMap(personClient::retrieveSlowEndpoint)
//                .sequential()
//                .collectList();

        // The below runs immediately all in parallel (max 10 at a time)
        Mono<List<PersonVo>> result = Flux.fromIterable(ids)
                .flatMap(personClient::retrieveSlowEndpoint, parallelSearchMaxThreads)
                .collectList();

        log.info("searchViaClient {}, result {}", ids, result);
        return result.log();
    }

}
