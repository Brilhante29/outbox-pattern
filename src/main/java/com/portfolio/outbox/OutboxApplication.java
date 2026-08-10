package com.portfolio.outbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;

import java.util.Set;

@SpringBootApplication
public class OutboxApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(OutboxApplication.class);
        if (args.length > 0 && Set.of("benchmark", "seed-crash", "reset").contains(args[0])) {
            application.setWebApplicationType(WebApplicationType.NONE);
        }
        application.run(args);
    }
}
