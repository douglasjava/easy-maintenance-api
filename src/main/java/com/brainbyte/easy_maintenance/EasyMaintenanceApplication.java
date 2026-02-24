package com.brainbyte.easy_maintenance;

import com.brainbyte.easy_maintenance.dashboard.application.DashboardProperties;
import com.brainbyte.easy_maintenance.infrastructure.saas.properties.AsaasProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@EnableConfigurationProperties({DashboardProperties.class, AsaasProperties.class})
public class EasyMaintenanceApplication {

	public static void main(String[] args) {
		SpringApplication.run(EasyMaintenanceApplication.class, args);
	}

}
