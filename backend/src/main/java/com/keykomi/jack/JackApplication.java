package com.keykomi.jack;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JackApplication {

	public static void main(String[] args) {
		SpringApplication.run(JackApplication.class, args);
	}

}
