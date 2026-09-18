package com.razeef.bugbrother;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.razeef.bugbrother.indexes.config.IndexConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.razeef.bugbrother.indexes.config.InternalApiConfiguration;


@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        IndexConfiguration.class,
        InternalApiConfiguration.class
})
public class FixhubIngestionApplication {

	public static void main(String[] args) {
		SpringApplication.run(FixhubIngestionApplication.class, args);
	}

}

