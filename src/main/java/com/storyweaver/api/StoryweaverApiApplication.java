package com.storyweaver.api;

import com.storyweaver.api.config.ApiConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties(ApiConfig.class)
@SpringBootApplication
public class StoryweaverApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(StoryweaverApiApplication.class, args);
	}

}
