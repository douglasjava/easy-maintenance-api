#!/bin/bash

echo "=== Gerando config do Alloy ==="

cat <<EOF > /app/alloy.config
prometheus.scrape "spring" {
  targets = [{
    __address__ = "127.0.0.1:8080",
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

echo "=== Iniciando Alloy ==="
/usr/local/bin/alloy run /app/alloy.config > /app/alloy.log 2>&1 &

echo "=== Iniciando Spring Boot ==="
java -jar app.jar
