# Observabilidade - Easy Maintenance

Este documento descreve a implementação de observabilidade no sistema utilizando Micrometer, Prometheus e Grafana Cloud.

## Stack de Observabilidade

- **Java 17+ / Spring Boot 3+**
- **Micrometer**: Fachada de métricas.
- **Prometheus**: Registry para exposição das métricas.
- **Grafana Cloud**: Visualização e alertas.
- **Actuator**: Endpoints de monitoramento.

## Como funciona a coleta

A aplicação expõe as métricas no formato Prometheus através do endpoint `/actuator/prometheus`. O Prometheus (ou o Grafana Cloud Agent) realiza o "scrape" (coleta) desses dados periodicamente.

### Configuração do Actuator

As métricas estão configuradas com o prefixo `easy_` e a tag global `application=easy-maintenance`.

Configurações principais em `application.properties`:
```properties
management.endpoints.web.exposure.include=health,info,prometheus,metrics
management.metrics.tags.application=easy-maintenance
```

## Lista de Métricas

### Métricas Técnicas (Padrão Spring Boot)

- `http_server_requests_seconds`: Latência e volume de requisições HTTP.
- `jvm_memory_used_bytes`: Uso de memória JVM.
- `jvm_gc_pause_seconds`: Pausas de Garbage Collection.
- `hikaricp_connections_active`: Conexões ativas no pool de banco de dados.
- `tomcat_threads_config_max_threads`: Threads do servidor web.

### Métricas de Negócio (Customizadas)

Todas as métricas customizadas possuem o prefixo `easy_`.

| Métrica | Tipo | Descrição | Tags |
|---------|------|-----------|------|
| `easy_subscriptions_created_total` | Counter | Total de assinaturas criadas | `plan` |
| `easy_subscriptions_canceled_total` | Counter | Total de assinaturas canceladas | `plan` |
| `easy_revenue_total` | Gauge | Receita total estimada (mensal) | - |
| `easy_active_subscriptions` | Gauge | Total de assinaturas ativas | - |
| `easy_items_created_total` | Counter | Total de itens de manutenção criados | - |
| `easy_ai_requests_total` | Counter | Total de requisições para a IA | - |
| `easy_ai_request_duration_seconds` | Timer | Latência das chamadas de IA | - |
| `easy_email_sent_total` | Counter | Total de e-mails enviados com sucesso | - |
| `easy_organizations_active` | Gauge | Total de organizações no sistema | - |
| `easy_users_active` | Gauge | Total de usuários no sistema | - |

## Como testar

1. Inicie a aplicação.
2. Acesse o endpoint de métricas:
   ```bash
   curl http://localhost:8080/actuator/prometheus
   ```
3. Verifique se as métricas com o prefixo `easy_` estão presentes na saída.

## Como conectar no Grafana Cloud

No Grafana Cloud, adicione uma configuração de scrape no seu `prometheus.yml` ou no Grafana Agent:

```yaml
scrape_configs:
  - job_name: 'easy-maintenance'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['api-host:8080']
```

## Alertas Recomendados

- **Erro de API**: `rate(http_server_requests_seconds_count{status=~"5.."}[5m]) / rate(http_server_requests_seconds_count[5m]) > 0.02`
- **Latência P95**: `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le)) > 1.2`
- **Queda de Envio de E-mails**: `rate(easy_email_sent_total[1h]) == 0`
- **Uso de IA Crítico**: `easy_ai_requests_total` monitorar anomalias de pico.

## Dashboards Sugeridos

1. **Business Overview**: Receita, Assinaturas Ativas, Crescimento de Usuários.
2. **Billing Health**: Taxa de cancelamento vs Assinaturas novas.
3. **AI Usage**: Performance da IA e volume de requisições.
4. **System Performance**: Latência de endpoints, Saúde da JVM e Pool de conexões.
