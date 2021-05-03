package lo.poc.webflux.webapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"lo.poc.webflux"})
public class WebMvcApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(WebMvcApplication.class);
        app.setWebApplicationType(WebApplicationType.REACTIVE);
        app.run(WebMvcApplication.class, args);
    }

}
