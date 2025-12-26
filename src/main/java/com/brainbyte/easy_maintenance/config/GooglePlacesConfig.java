package com.brainbyte.easy_maintenance.config;

import com.brainbyte.easy_maintenance.supplier.application.properties.GooglePlacesProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(GooglePlacesProperties.class)
public class GooglePlacesConfig {

  @Bean
  public WebClient googlePlacesWebClient(GooglePlacesProperties props) {
    return WebClient.builder()
            .baseUrl(props.getBaseUrl())
            .build();
  }

}
