# adapter-rest module

On this POC module the Controllers / WebFlux.fn Functions / WebFlux WebClient are contained.
This module is included on the actual applications, so that all of the applications work with the same code

# Summary
Below is a summary of what is included

* PersonController, @RestController which exposes various endpoints under `/persons`
* PersonService, @Service simulating synchronous (via Thread.sleep delay) and asynchronous (via Mono.fromCallable() with the delay on the caller) 
* RoutingConfiguration, WebFlux.fn @Configuration of RouterFunction<ServerResponse>. Endpoints under `/personsFunction`
* PersonClient, WebFlux WebClient which is able to call the exposed endpoints

# Details

## PersonController
The PersonController is a @RestController exposing a list of endpoints for testing sync and async calls to simulating blocking DB and REST calls.

The root path is `/persons` and the endpoints are exposed on both WebMVC and WebFlux applications

In summary, we have some endpoints which are async and do not block the thread (i.e. `/persons/{id}/service`) and some which are sync and block (i.e. `/persons/{id}/serviceblock`). This way we can see how the application is
working on parallel requests. Also the 'client' ones instead of using a service they use a WebFlux WebClient to call an external slow REST api in order to 
test and measure also external REST calls to other microservices.

The detailed endpoints exposed can be seen on the source code, see [PersonController.java](src/main/java/lo/poc/webflux/adapter/rest/controller/PersonController.java)

## PersonService
This is the @Service simulating synchronous (via Thread.sleep delay) and asynchronous (via Mono.fromCallable() with the delay on the caller)

The various methods exposed can be seen on the source code, see [PersonService.java](src/main/java/lo/poc/webflux/adapter/rest/service/PersonService.java)

## RoutingConfiguration
This is a WebFlux.fn @Configuration of RouterFunction<ServerResponse>.

The root path is `/personsFunction` and the endpoints are exposed ONLY on a WebFlux application, so not exposed on the WebMVC application.

The various endpoints exposed can be seen on the source code, see [RoutingConfiguration.java](src/main/java/lo/poc/webflux/adapter/rest/func/RoutingConfiguration.java)

## PersonClient
This is a WebFlux WebClient which is able to call the exposed endpoints.

Also included is the [PersonClient.java#main](src/main/java/lo/poc/webflux/adapter/rest/client/PersonClient.java#L73) method which can be used to simulate simultaneous parallel calls to each and every endpoint.
This method also measures the time spend for completing each endpoint and logs a summary on the console.

In order to call an individual application and/or individual endpoints you need to comment out the relevant applications and/or endpoints on the source code, i.e.
``` java
    public static void main(String[] args) {
...
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
...
    }
```

For example see a typical output below
```
19:39:08.579 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>>>>>>>>>> Start All
19:39:08.629 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>>>>>> Start for WebFlux, on http://localhost:8082
19:39:09.522 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>> Running for WebFlux, endpoint /{id}/service, on http://localhost:8082
19:39:12.831 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>> Finished 32 calls for WebFlux, endpoint /{id}/service... on 3309 ms
19:39:12.842 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>> Running for WebFlux, endpoint /{id}/serviceblock, on http://localhost:8082
19:39:28.928 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>> Finished 32 calls for WebFlux, endpoint /{id}/serviceblock... on 16086 ms
19:39:28.933 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>> Running for WebFlux, endpoint /{id}/servicesync, on http://localhost:8082
19:39:44.984 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>> Finished 32 calls for WebFlux, endpoint /{id}/servicesync... on 16051 ms
19:39:44.991 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>> Running for WebFlux, endpoint /{id}/client, on http://localhost:8082
19:39:47.066 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>> Finished 32 calls for WebFlux, endpoint /{id}/client... on 2075 ms
19:39:47.071 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>> Running for WebFlux, endpoint /{id}/clientsync, on http://localhost:8082
19:39:47.148 [reactor-http-nio-4] ERROR l.p.w.a.rest.client.PersonClient - >>>> ERROR: Error: 500 Internal Server Error from GET http://localhost:8082/persons/0/clientsync
19:39:47.148 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>> Finished 32 calls for WebFlux, endpoint /{id}/clientsync... on 77 ms
19:39:47.148 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>>>>>> End for WebFlux on 38519 ms
19:39:47.148 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>>>>>> Start for WebMVC, on http://localhost:8081
19:39:47.152 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>> Running for WebMVC, endpoint /{id}/service, on http://localhost:8081
19:39:49.211 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>> Finished 32 calls for WebMVC, endpoint /{id}/service... on 2059 ms
19:39:49.215 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>> Running for WebMVC, endpoint /{id}/serviceblock, on http://localhost:8081
19:40:05.309 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>> Finished 32 calls for WebMVC, endpoint /{id}/serviceblock... on 16094 ms
19:40:05.313 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>> Running for WebMVC, endpoint /{id}/servicesync, on http://localhost:8081
19:40:21.411 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>> Finished 32 calls for WebMVC, endpoint /{id}/servicesync... on 16099 ms
19:40:21.415 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>> Running for WebMVC, endpoint /{id}/client, on http://localhost:8081
19:40:23.506 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>> Finished 32 calls for WebMVC, endpoint /{id}/client... on 2091 ms
19:40:23.511 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>> Running for WebMVC, endpoint /{id}/clientsync, on http://localhost:8081
19:40:39.655 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>> Finished 32 calls for WebMVC, endpoint /{id}/clientsync... on 16144 ms
19:40:39.655 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>>>>>> End for WebMVC on 52507 ms
19:40:39.655 [main] INFO  l.p.w.a.rest.client.PersonClient - >>>>>>>>>>>> End ALL on 91079 ms
```
