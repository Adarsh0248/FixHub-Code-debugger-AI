package com.razeef.bugbrother;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FixhubIngestionApplication {

	public static void main(String[] args) {
		SpringApplication.run(FixhubIngestionApplication.class, args);
	}

}
