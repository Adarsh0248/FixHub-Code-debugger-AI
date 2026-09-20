package com.razeef.bugbrother;

import com.razeef.bugbrother.retrieval.config.ContextBudgetProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ContextBudgetProperties.class)
public class FixhubWorkerApplication {

	public static void main(String[] args) {
		SpringApplication.run(FixhubWorkerApplication.class, args);
	}

}
