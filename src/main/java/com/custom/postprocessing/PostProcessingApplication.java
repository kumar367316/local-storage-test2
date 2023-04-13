package com.custom.postprocessing;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;


/**
 * @author kumar.charanswain
 *
 */
@SpringBootApplication(exclude={SecurityAutoConfiguration.class})
@EnableScheduling	
public class PostProcessingApplication extends SpringBootServletInitializer {

	public static void main(String[] args) {
		SpringApplication.run(PostProcessingApplication.class, args);
	}
	
	 
    @Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder springApplicationBuilder) {
		return springApplicationBuilder.sources(PostProcessingApplication.class);
	}

	static {
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		System.setProperty("current.date.time", dateFormat.format(new Date()));
	}
}
