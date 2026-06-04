package com.dgcockpit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DgCockpitApplication {
    public static void main(String[] args) {
        SpringApplication.run(DgCockpitApplication.class, args);
    }
}
