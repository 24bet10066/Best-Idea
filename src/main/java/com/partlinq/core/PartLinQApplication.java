package com.partlinq.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the PartLinQ application.
 * A trust-graph powered technician-parts ecosystem platform.
 */
@SpringBootApplication
@EnableScheduling
@EnableCaching
public class PartLinQApplication {

	public static void main(String[] args) {
		SpringApplication.run(PartLinQApplication.class, args);
	}

}
