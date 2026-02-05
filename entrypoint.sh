#!/bin/bash

# Gera config do Alloy a partir da ENV
cat <<EOF > /app/alloy.config
prometheus.scrape "spring" {
  targets = [{
    __address__ = "localhost:8080",
    __metrics_path__ = "/actuator/prometheus",
    scheme = "http"
  }]

  scrape_interval = "30s"
}

prometheus.remote_write "grafana" {
  endpoint {
    url = "$PROM_URL"

    basic_auth {
      username = "$PROM_USER"
      password = "$PROM_PASS"
    }
  }
}
EOF

# Inicia Alloy em background
/usr/local/bin/alloy run /app/alloy.config &

# Inicia sua aplicação Spring
java -jar app.jar