package com.company.l2app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class L2AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(L2AuthApplication.class, args);
    }
}
