# POC for Spring webflux

On this POC the behaviour of WebFlux vs MVC is explored

# Summary
The repo contains a main 'library' module the `adapter-rest` and various spring boot app executables which depend on the library module.
As such all controllers defined on library module are exposed by the app modules.

Modules:
* adapter-rest - This is the library module containing the REST endpoints which are shared between the apps
* webapp - This is the spring MVC application using though webflux WebClient. 
  Max threads are 4 to be able to see how it can handle traffic with same threads as the WebFlux.
  It is configured to be run on http://localhost:8081
* webfluxapp - This is the spring WebFlux application. 
  It is configured to be run on http://localhost:8082
* mockserverapp - This is a spring MVC application containing the endpoint which takes a few seconds to complete. 
  Main purpose to be called by the webflux WebClient of other apps.
  It is configured to be run on http://localhost:8083

## How to setup
Start all spring boot applications, currently 3, and watch how they communicate. 

The `main` method of the `PersonClient` can be used to make multiple parallel calls to the endpoints exposed by the apps, 
see `adapter-rest/README.md` of how to configure it.

## Conclusion
During the analysis the following behaviour were observer.

### 1. Which starts, MVC or WebFlux?
Depending on the library you have on the classpath Spring Boot will auto bootstrap the following:

* WebFlux when there is only the `spring-boot-starter-webflux` on the classpath

* WebMVC when there is only the `spring-boot-starter-web` on the classpath

* WebMVC when there are both the `spring-boot-starter-web` and `spring-boot-starter-webflux` on the classpath.  
  See from https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-webflux that 
  > Adding both spring-boot-starter-web and spring-boot-starter-webflux modules in your application results in Spring Boot auto-configuring Spring MVC, not WebFlux. 
  > This behavior has been chosen because many Spring developers add spring-boot-starter-webflux to their Spring MVC application to use the reactive WebClient. 
  > You can still enforce your choice by setting the chosen application type to SpringApplication.setWebApplicationType(WebApplicationType.REACTIVE).

  However, even when `app.setWebApplicationType(WebApplicationType.REACTIVE);` is used WebMVC is started and the endpoint `/personsFunction/{id}` 
  defined via RouterFunction (WebFlux.fn) is not exposed.

* In order to have WebFlux running on tomcat see https://stackoverflow.com/questions/43472091/using-spring-boot-weblfux-with-embedded-tomcat
  We need to include only `spring-boot-starter-tomcat` and disable `spring-boot-starter-web`
  In this configuration the endpoint `/personsFunction/{id}` defined via RouterFunction (WebFlux.fn) is exposed.
  

### 2. Async responses when making 32 concurrent requests

In order to make individual calls we have to change the PersonClient.main() method to keep the calls we want. Additionally we need to enable logging

When you return `Mono` both WebMVC and WebFlux work fine. WebMVC works fine even with a small number of thread (4).
The main issue here is that the async should be all the way and you should have no blocking call from the moment the controller endpoint starts until it finishes.
This can be easily observed by calling endpoints `/persons/{id}/servicesync` and `persons/{id}/client`.
The former calls the service which blocks for x seconds, while the latter calls webflux client which in turn calls the mockserverapp which blocks for x seconds.
TODO: The test which can be started by `PersonClient.main()` consists of multiple parallel calls (32) which we expect to finish in a few seconds. The observation is the following:

#### WebMVC (max threads 4)

* On the call to `/persons/{id}/servicesync` we see 4 calls processed on every 2 seconds, so the test takes a lot of time to complete.
  This is expected since the endpoint thread is blocked and as such tomcat can only process 4 requests simultaneously
* On the call to `/persons/{id}/service` we see all calls are processed immediately. This is because the synchronous call is wrapped around
  `Mono.fromCallable(() -> ...` and as such the thread is not blocked. See https://projectreactor.io/docs/core/release/reference/#faq.wrap-blocking  
  This is expected since the endpoint thread is not blocked and as such Netty can process more than 4 requests simultaneously.
* On the call to `/persons/{id}/client` we see all calls are processed immediately, i.e. we see the log `retrieveViaClient 15, result MonoLogFuseable`
  prior to the actual calls via the client. If we observe on the logs we also see that only 4 threads are actually active `nio-8081-exec-X` on each time
  but since the threads are not blocked, tomcat is able to cope with or the requests without any problem.
  Up until this point we see that the `nio-8081-exec-X` threads are reused to make the `onSubscribe` and `request(unbounded)` event calls, and we see
  that all the 32 calls complete immediately, i.e. in around 0.5 second time.
  Then the webflux client makes the actual calls to the mockserverapp and as soon as the mockserverapp starts to respond we see the `ctor-http-nio-Y` threads
  are starting to respond with the `onNext(PersonVo...` and `onComplete()` events which finally respond the endpoint asynchronously,
  i.e. resuming the async response `[nio-8081-exec-3] s.w.s.m.m.a.RequestMappingHandlerAdapter : Resume with async result [PersonVo(id=15,`
  and finally responding `[nio-8081-exec-3] o.s.web.servlet.DispatcherServlet        : Exiting from "ASYNC" dispatch, status 200`  
  This is expected since the endpoint thread is now not blocked and as such tomcat can process more than 4 requests simultaneously
  since the results are returned asynchronously.

See log from server when calling `/persons/15/client`
```
2021-05-03 12:04:06.331 DEBUG [] 1808 --- [nio-8081-exec-2] o.s.web.servlet.DispatcherServlet        : GET "/persons/15/client", parameters={}
2021-05-03 12:04:06.332 DEBUG [] 1808 --- [nio-8081-exec-2] s.w.s.m.m.a.RequestMappingHandlerMapping : Mapped to lo.poc.webflux.adapter.rest.controller.PersonController#retrieveViaClient(Long)
2021-05-03 12:04:06.333  INFO [] 1808 --- [nio-8081-exec-2] l.p.w.a.r.controller.PersonController    : retrieveViaClient 15
2021-05-03 12:04:06.468  INFO [] 1808 --- [nio-8081-exec-2] l.p.w.a.r.controller.PersonController    : retrieveViaClient 15, result MonoLogFuseable
2021-05-03 12:04:06.476  INFO [] 1808 --- [nio-8081-exec-2] reactor.Mono.FlatMap.2                   : | onSubscribe([Fuseable] MonoFlatMap.FlatMapMain)
2021-05-03 12:04:06.479  INFO [] 1808 --- [nio-8081-exec-2] reactor.Mono.LogFuseable.3               : | onSubscribe([Fuseable] FluxPeekFuseable.PeekFuseableSubscriber)
2021-05-03 12:04:06.479  INFO [] 1808 --- [nio-8081-exec-2] reactor.Mono.LogFuseable.3               : | request(unbounded)
2021-05-03 12:04:06.479  INFO [] 1808 --- [nio-8081-exec-2] reactor.Mono.FlatMap.2                   : | request(unbounded)
2021-05-03 12:04:06.610 DEBUG [] 1808 --- [nio-8081-exec-2] o.s.w.r.f.client.ExchangeFunctions       : [69f590dc] HTTP GET http://localhost:8083/persons/15/servicesync
2021-05-03 12:04:07.487 DEBUG [] 1808 --- [nio-8081-exec-2] o.s.w.c.request.async.WebAsyncManager    : Started async request
2021-05-03 12:04:07.488 DEBUG [] 1808 --- [nio-8081-exec-2] o.s.web.servlet.DispatcherServlet        : Exiting but response remains open for further handling
2021-05-03 12:04:09.737 DEBUG [] 1808 --- [ctor-http-nio-2] o.s.w.r.f.client.ExchangeFunctions       : [69f590dc] Response 200 OK
2021-05-03 12:04:09.777 DEBUG [] 1808 --- [ctor-http-nio-2] o.s.http.codec.json.Jackson2JsonDecoder  : [69f590dc] Decoded [PersonVo(id=15, name=Name15, email=person15@test.lo, phone=+30 6974 000015)]
2021-05-03 12:04:09.777  INFO [] 1808 --- [ctor-http-nio-2] reactor.Mono.FlatMap.2                   : | onNext(PersonVo(id=15, name=Name15, email=person15@test.lo, phone=+30 6974 000015))
2021-05-03 12:04:09.777  INFO [] 1808 --- [ctor-http-nio-2] reactor.Mono.LogFuseable.3               : | onNext(PersonVo(id=15, name=Name15, email=person15@test.lo, phone=+30 6974 000015))
2021-05-03 12:04:09.777  INFO [] 1808 --- [ctor-http-nio-2] reactor.Mono.FlatMap.2                   : | onComplete()
2021-05-03 12:04:09.777  INFO [] 1808 --- [ctor-http-nio-2] reactor.Mono.LogFuseable.3               : | onComplete()
2021-05-03 12:04:09.777 DEBUG [] 1808 --- [ctor-http-nio-2] o.s.w.c.request.async.WebAsyncManager    : Async result set, dispatch to /persons/15/client
2021-05-03 12:04:09.777 DEBUG [] 1808 --- [nio-8081-exec-3] o.s.web.servlet.DispatcherServlet        : "ASYNC" dispatch for GET "/persons/15/client", parameters={}
2021-05-03 12:04:09.778 DEBUG [] 1808 --- [nio-8081-exec-3] s.w.s.m.m.a.RequestMappingHandlerAdapter : Resume with async result [PersonVo(id=15, name=Name15, email=person15@test.lo, phone=+30 6974 000015)]
2021-05-03 12:04:09.778 DEBUG [] 1808 --- [nio-8081-exec-3] m.m.a.RequestResponseBodyMethodProcessor : Using 'application/json', given [*/*] and supported [application/json, application/*+json, application/json, application/*+json]
2021-05-03 12:04:09.778 DEBUG [] 1808 --- [nio-8081-exec-3] m.m.a.RequestResponseBodyMethodProcessor : Writing [PersonVo(id=15, name=Name15, email=person15@test.lo, phone=+30 6974 000015)]
2021-05-03 12:04:09.779 DEBUG [] 1808 --- [nio-8081-exec-3] o.s.web.servlet.DispatcherServlet        : Exiting from "ASYNC" dispatch, status 200
```

#### WebFlux (max threads 4 which depend on the number of CPU)

see https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html#webflux-concurrency-model

* On the call to `/persons/{id}/servicesync` we see 4 calls processed on every 2 seconds, so the test takes a lot of time to complete.
  This is expected since the endpoint thread is blocked and as such Netty can only process 4 requests simultaneously
* On the call to `/persons/{id}/service` we see all calls are processed immediately. This is because the synchronous call is wrapped around
  `Mono.fromCallable(() -> ...` and as such the thread is not blocked. See https://projectreactor.io/docs/core/release/reference/#faq.wrap-blocking  
  This is expected since the endpoint thread is not blocked and as such Netty can process more than 4 requests simultaneously.
* On the call to `/persons/{id}/client` we see all calls are processed immediately, i.e. we see the log `retrieveViaClient 1, result MonoLogFuseable`
  prior to the actual calls via the client. If we observe on the logs we also see that only 4 threads are actually active `nio-8081-exec-X` on each time
  but since the threads are not blocked are able to cope with or the requests without any problem. Then the webflux client makes the actual calls
  to the mockserverapp.
  Up until this point we see that the `ctor-http-nio-X` threads are reused to make the `onSubscribe` and `request(unbounded)` event calls, and we see
  that all the 32 calls complete immediately, i.e. in around 0.5 second time.
  Then as soon as the mockserverapp starts to respond we see the same `ctor-http-nio-X` threads are starting to respond with the `onNext(PersonVo...`
  and `onComplete()` events which finally respond the endpoint asynchronously.
  See the initial request `[40a1fcc1-3] HTTP GET "/persons/15/client"` which is finally responded by `[40a1fcc1-3] Completed 200 OK`  
  This is expected since the endpoint thread is not blocked and as such Netty can process more than 4 requests simultaneously
  since the results are returned asynchronously, i.e. `[ctor-http-nio-3] o.s.w.s.adapter.HttpWebHandlerAdapter    : [40a1fcc1-3] Completed 200 OK`

See log from server when calling `/persons/15/client`
```
2021-05-03 12:04:48.919 DEBUG [] 15052 --- [ctor-http-nio-3] o.s.w.s.adapter.HttpWebHandlerAdapter    : [40a1fcc1-3] HTTP GET "/persons/15/client"
2021-05-03 12:04:48.920 DEBUG [] 15052 --- [ctor-http-nio-3] s.w.r.r.m.a.RequestMappingHandlerMapping : [40a1fcc1-3] Mapped to lo.poc.webflux.adapter.rest.controller.PersonController#retrieveViaClient(Long)
2021-05-03 12:04:48.920  INFO [] 15052 --- [ctor-http-nio-3] l.p.w.a.r.controller.PersonController    : retrieveViaClient 15
2021-05-03 12:04:48.922  INFO [] 15052 --- [ctor-http-nio-3] l.p.w.a.r.controller.PersonController    : retrieveViaClient 15, result MonoLogFuseable
2021-05-03 12:04:48.922 DEBUG [] 15052 --- [ctor-http-nio-3] o.s.w.r.r.m.a.ResponseBodyResultHandler  : [40a1fcc1-3] Using 'application/json' given [*/*] and supported [application/json, application/*+json, application/x-ndjson, text/event-stream]
2021-05-03 12:04:48.922 DEBUG [] 15052 --- [ctor-http-nio-3] o.s.w.r.r.m.a.ResponseBodyResultHandler  : [40a1fcc1-3] 0..1 [lo.poc.webflux.adapter.rest.controller.domain.PersonVo]
2021-05-03 12:04:48.923  INFO [] 15052 --- [ctor-http-nio-3] reactor.Mono.FlatMap.4                   : | onSubscribe([Fuseable] MonoFlatMap.FlatMapMain)
2021-05-03 12:04:48.923  INFO [] 15052 --- [ctor-http-nio-3] reactor.Mono.LogFuseable.5               : | onSubscribe([Fuseable] FluxPeekFuseable.PeekFuseableSubscriber)
2021-05-03 12:04:48.923  INFO [] 15052 --- [ctor-http-nio-3] reactor.Mono.LogFuseable.5               : | request(unbounded)
2021-05-03 12:04:48.923  INFO [] 15052 --- [ctor-http-nio-3] reactor.Mono.FlatMap.4                   : | request(unbounded)
2021-05-03 12:04:48.923 DEBUG [] 15052 --- [ctor-http-nio-3] o.s.w.r.f.client.ExchangeFunctions       : [4f26352c] HTTP GET http://localhost:8083/persons/15/servicesync
2021-05-03 12:04:50.951 DEBUG [] 15052 --- [ctor-http-nio-1] o.s.w.r.f.client.ExchangeFunctions       : [4f26352c] Response 200 OK
2021-05-03 12:04:50.956 DEBUG [] 15052 --- [ctor-http-nio-1] org.springframework.web.HttpLogging      : [4f26352c] Decoded [PersonVo(id=15, name=Name15, email=person15@test.lo, phone=+30 6974 000015)]
2021-05-03 12:04:50.958  INFO [] 15052 --- [ctor-http-nio-1] reactor.Mono.FlatMap.4                   : | onNext(PersonVo(id=15, name=Name15, email=person15@test.lo, phone=+30 6974 000015))
2021-05-03 12:04:50.958  INFO [] 15052 --- [ctor-http-nio-1] reactor.Mono.LogFuseable.5               : | onNext(PersonVo(id=15, name=Name15, email=person15@test.lo, phone=+30 6974 000015))
2021-05-03 12:04:50.958 DEBUG [] 15052 --- [ctor-http-nio-1] org.springframework.web.HttpLogging      : [40a1fcc1-3] Encoding [PersonVo(id=15, name=Name15, email=person15@test.lo, phone=+30 6974 000015)]
2021-05-03 12:04:50.959  INFO [] 15052 --- [ctor-http-nio-1] reactor.Mono.FlatMap.4                   : | onComplete()
2021-05-03 12:04:50.959  INFO [] 15052 --- [ctor-http-nio-1] reactor.Mono.LogFuseable.5               : | onComplete()
2021-05-03 12:04:50.960 DEBUG [] 15052 --- [ctor-http-nio-3] o.s.w.s.adapter.HttpWebHandlerAdapter    : [40a1fcc1-3] Completed 200 OK
```

### 3. Making 32 concurrent requests and the results

If we call the `PersonClient.main()` method it will call all endpoints.

We can see from the log that the *sync* operations take around 16 seconds to complete, which is explained by the fact that 
we only have 4 threads on the server, and these are blocked. 
In more detail the 32 requests will be completed in 32/4 = 8 passes. Each pass will take 2 seconds to complete (since the slow endpoint/service delays 2 seconds).
So finally we will need around 8*2 = 16 seconds.

However, the *async* operations take around 2 seconds to complete, which is explained by the fact that event though we only 
have 4 thread on the server, these are not blocked so all requests are processed in parallel. 
In more detail the requests to the slow endpoint are sent in parallel and the responses are returned as soon as the slow endpoint responds.

One final discovery is that the endpoint `/{id}/clientsync` fails on WebFlux, reason being that we try to use block() on the Mono of the personClient. 
This is not supported so on the server and we observe the following exception
```
2021-05-01 19:32:13.153 ERROR [] 2872 --- [ctor-http-nio-1] a.w.r.e.AbstractErrorWebExceptionHandler : [39e88066-1457]  500 Server Error for HTTP GET "/persons/26/clientsync"

java.lang.IllegalStateException: block()/blockFirst()/blockLast() are blocking, which is not supported in thread reactor-http-nio-1
	at reactor.core.publisher.BlockingSingleSubscriber.blockingGet(BlockingSingleSubscriber.java:83) ~[reactor-core-3.4.3.jar:3.4.3]
	Suppressed: reactor.core.publisher.FluxOnAssembly$OnAssemblyException: 
Error has been observed at the following site(s):
	|_ checkpoint ⇢ HTTP GET "/persons/26/clientsync" [ExceptionHandlingWebHandler]
Stack trace:
		at reactor.core.publisher.BlockingSingleSubscriber.blockingGet(BlockingSingleSubscriber.java:83) ~[reactor-core-3.4.3.jar:3.4.3]
		at reactor.core.publisher.Mono.block(Mono.java:1703) ~[reactor-core-3.4.3.jar:3.4.3]
		at lo.poc.webflux.adapter.rest.controller.PersonController.retrieveViaClientSync(PersonController.java:89) ~[classes/:na]
		at sun.reflect.GeneratedMethodAccessor52.invoke(Unknown Source) ~[na:na]
		at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43) ~[na:1.8.0_162]
		at java.lang.reflect.Method.invoke(Method.java:498) ~[na:1.8.0_162]
		at org.springframework.web.reactive.result.method.InvocableHandlerMethod.lambda$invoke$0(InvocableHandlerMethod.java:146) ~[spring-webflux-5.3.4.jar:5.3.4]
...
```

See below the log of the PersonClient.main() execution
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

# References

The following references were used
* Spring WebFlux, especially section 1.1 - https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html#webflux
* Spring WebFlux, Concurrency Model - https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html#webflux-concurrency-model
* Spring WebFlux, Spring MVC or WebFlux? - https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html#webflux-framework-choice
* Spring-webflux and springboot-start-web issues - https://programmersought.com/article/27886319313/
* How to Wrap a Synchronous, Blocking Call - https://projectreactor.io/docs/core/release/reference/#faq.wrap-blocking
* Baeldung, Simultaneous Spring WebClient Calls - https://www.baeldung.com/spring-webclient-simultaneous-calls
