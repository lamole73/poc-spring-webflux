package lo.poc.webflux.adapter.rest.func;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration(proxyBeanMethods = false)
public class RoutingConfiguration {
    @Bean
    public RouterFunction<ServerResponse> monoRouterFunction(PersonHandler userHandler) {
        return route(GET("/personsFunction/{id}").and(RequestPredicates.accept(APPLICATION_JSON)), userHandler::receivePersonSync);
    }
}
