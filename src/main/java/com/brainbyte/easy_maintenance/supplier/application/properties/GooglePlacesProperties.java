package com.brainbyte.easy_maintenance.supplier.application.properties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ConfigurationProperties(prefix = "google.places")
public class GooglePlacesProperties {

  private String apiKey;
  private String baseUrl;
  private int defaultRadiusM = 20000;
  private int maxResults = 8;
  private boolean detailsEnabled = true;

}
