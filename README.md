# Ledger Service - Sistema de Contabilidade Simplificado

Este projeto implementa um serviço de contabilidade (Ledger) robusto e escalável para gerenciar contas de usuários e transações financeiras. Ele foi projetado seguindo as melhores práticas de arquitetura de microsserviços, com foco em resiliência, performance e observabilidade.

## 🚀 Tecnologias e Ferramentas

O projeto utiliza uma stack tecnológica moderna e completa:

### Backend & Frameworks
*   **Java 21:** Linguagem base, aproveitando as últimas features de performance e sintaxe.
*   **Spring Boot 3:** Framework principal para injeção de dependência, configuração e execução.
*   **Spring Data JPA / Hibernate:** Camada de persistência e ORM.
*   **Spring Web:** Construção da API RESTful. 5 Niveis de Maturidade Richardson.
*   **Spring HATEOAS:** Implementação de hipermídia na API.
*   **Flyway:** Gerenciamento e versionamento de migrações de banco de dados.

### Infraestrutura & Dados
*   **PostgreSQL:** Banco de dados relacional principal.
*   **Redis:** Cache distribuído para alta performance em leituras.
*   **Apache Kafka:** Plataforma de streaming de eventos para comunicação assíncrona e notificações.
*   **Docker & Docker Compose:** Containerização e orquestração do ambiente de desenvolvimento.

### Observabilidade & Monitoramento
*   **Grafana:** Visualização de métricas e dashboards em tempo real.
*   **Prometheus:** Coleta e armazenamento de métricas da aplicação.
*   **Datadog:** Integração configurada para monitoramento avançado (APM, logs, métricas).
*   **Micrometer:** Fachada de métricas para instrumentação da aplicação.

### Segurança & Qualidade de Código
*   **Veracode:** Análise estática de segurança (SAST) para identificar vulnerabilidades no código.
*   **SonarQube:** Inspeção contínua da qualidade do código, detectando bugs, code smells e vulnerabilidades de segurança.
*   **Snyk:** Monitoramento de vulnerabilidades em dependências open source (SCA) e contêineres.

### Testes & Qualidade
*   **JUnit 5:** Framework de testes unitários.
*   **Mockito:** Framework de mocking para testes isolados.
*   **Testcontainers:** Testes de integração com containers reais (Postgres, Kafka).
*   **k6:** Ferramenta para testes de carga e performance.

---

## 🏗️ Visão Geral da Arquitetura

O sistema segue uma **Arquitetura Hexagonal (Ports and Adapters)**, garantindo que a lógica de negócio (Domínio) permaneça isolada de detalhes de infraestrutura e frameworks externos.

### Principais Características da Arquitetura:
*   **Domínio Isolado:** As entidades e regras de negócio residem no núcleo da aplicação, sem dependências de frameworks externos.
*   **Portas (Ports):** Interfaces que definem os contratos de entrada (casos de uso) e saída (persistência, mensageria).
*   **Adaptadores (Adapters):** Implementações concretas das portas.
    *   **Adaptadores de Entrada (Driving):** Controllers REST, Listeners Kafka.
    *   **Adaptadores de Saída (Driven):** Repositórios JPA, Produtores Kafka, Clientes de E-mail.
*   **Orientação a Eventos:** O sistema utiliza eventos de domínio para desacoplar processos complexos, como a transferência de fundos, garantindo consistência eventual e alta disponibilidade.

### Componentes Chave:
-   **API REST:** Interface principal para interação com o sistema.
-   **Processamento Assíncrono (Outbox Pattern):** As transferências são salvas em uma tabela `tb_outbox` e processadas de forma assíncrona, garantindo resiliência e consistência.
-   **Cache (Redis):** Otimização de leituras frequentes.
-   **Mensageria (Kafka):** Notificações e comunicação assíncrona entre domínios.

---

## ✨ Funcionalidades Principais

-   **Gerenciamento de Usuários:** CRUD completo para usuários.
-   **Gerenciamento de Contas:**
    -   Criação automática de conta ao registrar um novo usuário.
    -   Consulta de saldo e detalhes da conta.
    -   Inativação de contas.
-   **Transferências Financeiras:**
    -   Endpoint para solicitar transferências entre contas.
    -   Processamento assíncrono e seguro das transferências.
    -   Notificação por e-mail (simulada) para remetente e destinatário.

---

## 🛠️ Como Executar o Projeto Localmente

### Pré-requisitos

-   Java 21+
-   Maven 3.8+
-   Docker e Docker Compose

### 1. Subindo a Infraestrutura

O `docker-compose.yml` na raiz do projeto orquestra todos os serviços necessários (PostgreSQL, Redis, Kafka, Prometheus, Grafana).

Para iniciar toda a infraestrutura, execute:

```sh
docker-compose up -d
```

Isso irá iniciar todos os serviços em background.

### 2. Executando a Aplicação

Com a infraestrutura rodando, você pode iniciar a aplicação Spring Boot.

**Via Maven:**

```sh
./mvnw spring-boot:run
```

**Via IDE:**
Execute a classe principal `LedgerServiceApplication.java`.

A API estará disponível em `http://localhost:8082/api/v1`.

### 3. Acessando os Serviços Auxiliares

-   **Documentação da API (Swagger):** `http://localhost:8082/api/v1/swagger-ui.html`
-   **Grafana:** `http://localhost:3000` (login: `admin`/`admin`)
    -   O dashboard "JVM (Micrometer)" já vem pré-configurado.
-   **Prometheus:** `http://localhost:9090`

### 4. Importando Requisições (Insomnia)

Para facilitar os testes manuais da API, um arquivo de coleção do Insomnia está incluído na raiz do projeto.

-   **Arquivo:** `insomnia_collection_ledger.json`
-   **Como usar:** Abra o Insomnia, vá em `Application` -> `Preferences` -> `Data` -> `Import Data` -> `From File` e selecione o arquivo JSON. Todas as rotas configuradas estarão prontas para uso.

---

## ✅ Testes

### Testes Unitários e de Integração

Para rodar todos os testes unitários e de integração, utilize o comando Maven:

```sh
./mvnw test
```

### Teste de Carga (k6)

O projeto inclui um script de teste de carga simples usando k6.

1.  **Instale o k6:** Siga as instruções em `k6.io`.
2.  **Execute o teste:**

    ```sh
    k6 run load-test.js
    ```

Isso irá simular múltiplos usuários criando contas e realizando transferências, ajudando a validar a performance e a resiliência do sistema sob carga.

---

## 📂 Estrutura do Projeto

```
.
├── src
│   ├── main
│   │   ├── java/com/astropay
│   │   │   ├── application         # Casos de Uso, DTOs, Services (Application Layer)
│   │   │   ├── domain              # Entidades, Regras de Negócio (Domain Layer)
│   │   │   └── infrastructure      # Configurações, Adaptadores (Infrastructure Layer)
│   │   └── resources
│   │       ├── application.properties
│   │       └── db/migration        # Scripts do Flyway
│   └── test                      # Testes unitários e de integração
├── docker-compose.yml              # Orquestração da infraestrutura local
├── insomnia_collection_ledger.json # Coleção de requisições para Insomnia
├── pom.xml                         # Dependências e build do projeto
└── README.md                       # Este arquivo
```

## 🔄 Fluxos de Negócio Importantes

### 1. Criação de Usuário

1.  `POST /users` é chamado.
2.  `UserServiceImpl` valida os dados e salva um novo `User`.
3.  Imediatamente, `UserServiceImpl` chama `AccountService.createAccountForUser` para criar uma conta associada, com um saldo inicial padrão.
4.  `AccountService` dispara um evento `AccountCreatedEvent` para o Kafka.
5.  `AccountCreatedEventListener` consome o evento e (simula) o envio de um e-mail de boas-vindas.

### 2. Transferência de Dinheiro

1.  `POST /transfers` é chamado.
2.  `TransferController` retorna `202 Accepted` imediatamente.
3.  `TransferServiceImpl` **não** executa a transferência. Ele cria um `OutboxEvent` e o salva na tabela `tb_outbox` na mesma transação.
4.  `TransferEventScheduler` (rodando a cada 2 segundos) busca eventos da `tb_outbox`.
5.  Para cada evento, o scheduler:
    a. Cria uma `Transaction` com status `PENDING` e a salva.
    b. Tenta executar o débito e o crédito nas contas.
    c. Atualiza a `Transaction` para `SUCCESS` ou `FAILED`.
    d. Dispara um evento `TransactionEvent` para o Kafka.
6.  `TransactionEventListener` consome o evento e (simula) o envio de e-mails de notificação para o remetente e o destinatário.
