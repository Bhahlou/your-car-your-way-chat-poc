package com.yourcaryourway.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the modular monolith. Each direct sub-package (accounts, support) is a module:
 * its boundaries are verified by {@code ModularityTests}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ChatPocApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatPocApplication.class, args);
    }
}
