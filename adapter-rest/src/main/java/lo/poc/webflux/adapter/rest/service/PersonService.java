package lo.poc.webflux.adapter.rest.service;

import lo.poc.webflux.adapter.rest.controller.domain.PersonVo;
import lo.poc.webflux.adapter.rest.error.PersonNotFoundException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Service
@Slf4j
public class PersonService {
    private static final Long MAX_ID = Long.parseLong("10000");


    @Value("${pocconfig.personservice.block.delay}")
    private Long delay = 2000L;

    public Mono<PersonVo> retrieveAsync(@NonNull Long id) throws PersonNotFoundException {
        log.info("retrieveAsync {}", id);

        // Using Mono.delayElement
//        PersonVo entity = retrieve(id);
//        if (delay > 0) {
//            log.info("retrieveAsync {}, with Mono.delayElement for {} ms", id, delay);
//        }
//        log.info("retrieveAsync {}, result {}", id, entity);
//        return Mono.just(entity).delayElement(Duration.ofMillis(delay));

        // Using Mono.fromCallable
        Mono<PersonVo> entity = Mono.fromCallable(() -> retrieveSync(id)).subscribeOn(Schedulers.boundedElastic());
        log.info("retrieveAsync {}, result {}", id, entity);
        return entity;
    }

    public Mono<PersonVo> retrieveAsyncBlocking(@NonNull Long id) throws PersonNotFoundException {
        log.info("retrieveAsyncBlocking {}", id);

        PersonVo entity = retrieveSync(id);

        log.info("retrieveAsyncBlocking {}, result {}", id, entity);
        return Mono.just(entity);
    }

    public PersonVo retrieveSync(@NonNull Long id) throws PersonNotFoundException {
        log.info("retrieveSync {}", id);
        PersonVo entity = retrieve(id);

        try {
            if (delay > 0) {
                log.info("retrieveSync {}, sleeping for {}", id, delay);
                Thread.sleep(delay);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        log.info("retrieveSync {}, result {}", id, entity);
        return entity;
    }

    private PersonVo retrieve(@NonNull Long id) throws PersonNotFoundException {
        log.info("retrieve {}", id);
        if (MAX_ID.compareTo(id) < 1) {
            throw new PersonNotFoundException("Cannot find person with id " + id);
        }
        PersonVo entity = construct(id);

        log.info("retrieve {}, result {}", id, entity);
        return entity;
    }

    private PersonVo construct(Long id) {
        return PersonVo.builder()
                .id(id)
                .email("person" + id + "@test.lo")
                .name("Name" + id)
                .phone("+30 6974 " + StringUtils.leftPad("" + id, 6, "0"))
                .build();
    }

}
