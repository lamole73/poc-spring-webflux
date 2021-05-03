package lo.poc.webflux.adapter.rest.func;

import lo.poc.webflux.adapter.rest.client.PersonClient;
import lo.poc.webflux.adapter.rest.controller.domain.PersonVo;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
public class PersonHandler {

    PersonClient personClient;

    public Mono<ServerResponse> receivePersonSync(ServerRequest request) {
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                .body(personClient.retrieveSlowEndpoint(Long.parseLong(request.pathVariable("id"))), PersonVo.class);
    }

}
