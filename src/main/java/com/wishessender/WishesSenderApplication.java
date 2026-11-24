package com.wishessender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Starting point of the app.
 * Run it, then open http://localhost:8080 in the browser.
 */
@SpringBootApplication
@EnableScheduling   // turns on the @Scheduled reminder job
public class WishesSenderApplication {
    public static void main(String[] args) {
        SpringApplication.run(WishesSenderApplication.class, args);
    }
}