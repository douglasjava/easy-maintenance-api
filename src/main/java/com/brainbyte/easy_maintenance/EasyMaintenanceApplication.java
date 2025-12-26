package com.brainbyte.easy_maintenance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class EasyMaintenanceApplication {

	public static void main(String[] args) {
		SpringApplication.run(EasyMaintenanceApplication.class, args);
	}

}
