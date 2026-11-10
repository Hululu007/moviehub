package com.moviehub.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MovieHubServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(MovieHubServerApplication.class, args);
    }
}
