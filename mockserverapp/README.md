# mockserverapp Application

On this POC module the Mock Server is run.
It is a Spring Boot WebMVC application used as a simulation of an external slow microservice.

# Summary
All methods contained on the [adapter-rest module](../adapter-rest) are exposed by this application,
however the typical usage is the call to the slow endpoint `/persons/{id}/servicesync` which delays for 2 seconds, i.e. http://localhost:8083/persons/15/servicesync

The dafault port is: 8083

# Configuration
The configuration of delays and port can be changed on [application.yml](src/main/resources/application.yml) file
