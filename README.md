# Notification Service

Microsserviço responsável por notificar usuários por e-mail sobre o resultado do processamento de vídeos. Faz parte de uma arquitetura orientada a eventos, consumindo mensagens de uma fila SQS (publicadas via SNS) e enviando e-mails transacionais via Gmail SMTP.

## Visão geral

O fluxo principal do serviço é:

```
SNS → SQS (notification-queue) → NotificationConsumer → EmailService → Usuário
```

1. Um evento `VideoCompletedEvent` é publicado no SNS por outro microsserviço após o processamento de um vídeo
2. O SNS entrega a mensagem na fila SQS `notification-queue`
3. O `NotificationConsumer` faz polling da fila a cada 5 segundos
4. O `EmailService` envia um e-mail HTML ao usuário informando se o vídeo foi processado com sucesso (`READY`) ou se ocorreu um erro (`ERROR`)
5. Em caso de sucesso, a mensagem é removida da fila

## Stack

| Tecnologia | Uso |
|---|---|
| Kotlin 2.x + Spring Boot 4 | Framework principal |
| AWS SDK v2 (SQS) | Consumo de mensagens |
| Spring Mail (Gmail SMTP) | Envio de e-mails |
| OpenTelemetry | Rastreamento distribuído |
| Micrometer + Prometheus | Métricas |
| Spring Boot Actuator | Health checks |
| JaCoCo + SonarQube | Qualidade de código |
| Docker | Containerização |
| GitHub Actions | CI/CD |

## Estrutura do projeto

```
src/main/kotlin/.../
├── NotificationServiceApplication.kt   # Entry point
├── config/
│   ├── SqsConfig.kt                    # Bean do SqsClient
│   └── SqsProperties.kt               # Propriedades de configuração do SQS
├── consumer/
│   └── NotificationConsumer.kt         # Polling e processamento de mensagens
├── dto/
│   ├── SnsMessage.kt                   # Estrutura da mensagem SNS
│   └── VideoCompletedEvent.kt          # Evento de conclusão de vídeo
└── service/
    └── EmailService.kt                 # Envio de e-mails HTML
```

## Configuração

As principais configurações ficam em `application.yaml` e são sobrescritas por variáveis de ambiente em produção:

### SQS

| Variável de ambiente | Descrição | Padrão (local) |
|---|---|---|
| `SQS_ENDPOINT` | Endpoint do SQS | `http://localhost:4566` (LocalStack) |
| `SQS_REGION` | Região AWS | `us-east-1` |
| `SQS_ACCESS_KEY` | Access key | — |
| `SQS_SECRET_KEY` | Secret key | — |
| `SQS_NOTIFICATION_QUEUE` | Nome da fila | `notification-queue` |

### E-mail (Gmail SMTP)

| Variável de ambiente | Descrição |
|---|---|
| `MAIL_USERNAME` | Endereço Gmail |
| `MAIL_PASSWORD` | Senha de app do Gmail |

### OpenTelemetry

| Propriedade | Valor padrão |
|---|---|
| Endpoint OTLP | `http://localhost:4318/v1/traces` |
| Sampling | 100% |

## Executando localmente

### Pré-requisitos

- JDK 21
- Docker (para LocalStack e coletor OTEL, opcional)

### Com Gradle

```bash
./gradlew bootRun
```

### Com Docker

```bash
docker build -t notification-service .
docker run -p 8083:8083 \
  -e MAIL_USERNAME=seu@gmail.com \
  -e MAIL_PASSWORD=sua-senha-de-app \
  notification-service
```

A aplicação sobe na porta **8083**.

## Endpoints de observabilidade

| Endpoint | Descrição |
|---|---|
| `GET /actuator/health` | Status da aplicação |
| `GET /actuator/metrics` | Métricas disponíveis |
| `GET /actuator/prometheus` | Métricas no formato Prometheus |

## Testes

```bash
./gradlew test
```

O relatório de cobertura (JaCoCo) é gerado em `build/reports/jacoco/`.

## CI/CD

O pipeline no GitHub Actions (`.github/workflows/build.yaml`) executa automaticamente em push para `main`:

1. **Build**: compila, roda os testes e análise SonarQube
2. **Push**: publica a imagem Docker no Docker Hub em `felixgilioli/notification-service` com as tags `latest` e `v{run_number}`

> Requer os secrets `SONAR_TOKEN`, `DOCKERHUB_USERNAME` e `DOCKERHUB_TOKEN` configurados no repositório.
