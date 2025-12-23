# Ledger Service - Simplified Accounting System

> **⚠️ EDUCATIONAL PROJECT DISCLAIMER**
>
> This project is designed for **educational and demonstration purposes**. While it implements enterprise-grade patterns (Hexagonal Architecture, Event-Driven Design, Observability) and technologies (Kafka, Redis, Spring Boot 3), it is intended to serve as a reference for learning and experimentation. It is not a production-ready banking system.

This project implements a robust and scalable Ledger service to manage user accounts and financial transactions. It is designed following the best practices of microservices architecture, with a focus on resilience, performance, and observability.

## 🚀 Technologies and Tools

The project uses a modern and complete technology stack:

### Backend & Frameworks
*   **Java 21:** Base language, leveraging the latest performance and syntax features.
*   **Spring Boot 3:** Main framework for dependency injection, configuration, and execution.
*   **Spring Data JPA / Hibernate:** Persistence layer and ORM.
*   **Spring Web:** Building the RESTful API. 5 Levels of Richardson Maturity Model.
*   **Spring HATEOAS:** Implementation of hypermedia in the API.
*   **Flyway:** Database migration and versioning management.

### Infrastructure & Data
*   **PostgreSQL:** Main relational database.
*   **Redis:** Distributed cache for high performance in reads.
*   **Apache Kafka:** Event streaming platform for asynchronous communication and notifications.
*   **Docker & Docker Compose:** Containerization and orchestration of the development environment.

### Observability & Monitoring
*   **Grafana:** Real-time visualization of metrics and dashboards.
*   **Prometheus:** Collection and storage of application metrics.
*   **Datadog:** Configured integration for advanced monitoring (APM, logs, metrics).
*   **Micrometer:** Metrics facade for application instrumentation.

### Security & Code Quality
*   **Veracode:** Static Application Security Testing (SAST) to identify vulnerabilities in the code.
*   **SonarQube:** Continuous inspection of code quality, detecting bugs, code smells, and security vulnerabilities.
*   **Snyk:** Monitoring of vulnerabilities in open source dependencies (SCA) and containers.

### Testing & Quality
*   **JUnit 5:** Unit testing framework.
*   **Mockito:** Mocking framework for isolated tests.
*   **Testcontainers:** Integration tests with real containers (Postgres, Kafka).
*   **k6:** Tool for load and performance testing.

---

## 🏗️ Architecture & Documentation

The system follows a **Hexagonal Architecture (Ports and Adapters)**, ensuring that the business logic (Domain) remains isolated from infrastructure details and external frameworks.

For a complete technical overview, including architecture, design patterns, and performance metrics, please see the **[Technical Architecture and Engineering Report](TECHNICAL_REPORT.md)**.

### Key Architectural Features:
*   **Isolated Domain:** Business entities and rules reside at the core of the application, without dependencies on external frameworks.
*   **Ports:** Interfaces that define the contracts for input (use cases) and output (persistence, messaging).
*   **Adapters:** Concrete implementations of the ports.
    *   **Driving Adapters:** REST Controllers, Kafka Listeners.
    *   **Driven Adapters:** JPA Repositories, Kafka Producers, Email Clients.
*   **Event-Driven:** The system uses domain events to decouple complex processes, such as fund transfers, ensuring eventual consistency and high availability.

### Key Components:
-   **REST API:** Main interface for interacting with the system.
-   **Asynchronous Processing (Outbox Pattern):** Transfers are saved in a `tb_outbox` table and processed asynchronously, ensuring resilience and consistency.
-   **Cache (Redis):** Optimization of frequent reads.
-   **Messaging (Kafka):** Notifications and asynchronous communication between domains.

---

## ✨ Main Features

-   **User Management:** Complete CRUD for users.
-   **Account Management:**
    -   Automatic account creation when registering a new user.
    -   Querying account balance and details.
    -   Deactivation of accounts.
-   **Financial Transfers:**
    -   Endpoint to request transfers between accounts.
    -   Asynchronous and secure processing of transfers.
    -   Email notification (simulated) for sender and receiver.

---

## 🛠️ How to Run the Project Locally

### Prerequisites

-   Java 21+
-   Maven 3.8+
-   Docker and Docker Compose

### 1. Starting the Infrastructure

The `docker-compose.yml` at the root of the project orchestrates all necessary services (PostgreSQL, Redis, Kafka, Prometheus, Grafana).

To start the entire infrastructure, run:

```sh
docker-compose up -d
```

This will start all services in the background.

### 2. Running the Application

With the infrastructure running, you can start the Spring Boot application.

**Via Maven:**

```sh
./mvnw spring-boot:run
```

**Via IDE:**
Run the main class `LedgerServiceApplication.java`.

The API will be available at `http://localhost:8082/api/v1`.

### 3. Performance Tuning (VM Options)

To achieve maximum performance and throughput when running locally (especially for load testing), it is recommended to use the following VM Options. These settings optimize the Garbage Collector (G1GC), Heap memory, and JIT compiler for high-concurrency scenarios.

Add these to your IDE's Run Configuration or pass them via command line:

```
-javaagent:dd-java-agent.jar -Ddd.logs.injection=true -server -Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -XX:+UseJVMCICompiler -Dspring.output.ansi.enabled=always -Dcom.sun.management.jmxremote -Dspring.jmx.enabled=true -Dspring.liveBeansView.mbeanDomain -Dspring.application.admin.enabled=true
```

**Explanation:**
*   `-javaagent:dd-java-agent.jar`: Enables the Datadog Agent for APM and Profiling.
*   `-Ddd.logs.injection=true`: Injects Trace IDs into logs for correlation.
*   `-server`: Enables the C2 compiler for long-running performance.
*   `-Xms2g -Xmx4g`: Allocates a generous Heap (2GB min, 4GB max) to avoid frequent GCs under load.
*   `-XX:+UseG1GC`: Uses the G1 Garbage Collector, optimized for large heaps and low latency.
*   `-XX:MaxGCPauseMillis=200`: Instructs G1 to aim for pause times under 200ms.
*   `-XX:+UseJVMCICompiler`: Enables the Graal JIT compiler (if available) for further optimization.

### 4. Accessing Auxiliary Services

-   **API Documentation (Swagger):** `http://localhost:8082/api/v1/swagger-ui.html`
-   **Grafana:** `http://localhost:3000` (login: `admin`/`admin`)
    -   The "JVM (Micrometer)" dashboard is pre-configured.
-   **Prometheus:** `http://localhost:9090`
-   **Zipkin:** `http://localhost:9411` (Distributed Tracing)

### 5. Importing Requests (Insomnia)

To facilitate manual API testing, an Insomnia collection file is included at the root of the project.

-   **File:** `insomnia_collection_ledger.json`
-   **How to use:** Open Insomnia, go to `Application` -> `Preferences` -> `Data` -> `Import Data` -> `From File` and select the JSON file. All configured routes will be ready to use.

---

## ✅ Tests

### Unit and Integration Tests

To run all unit and integration tests, use the Maven command:

```sh
./mvnw test
```

### Load Testing (k6)

The project includes a simple load test script using k6.

1.  **Install k6:** Follow the instructions at `k6.io`.
2.  **Run the test:**

    ```sh
    k6 run load-test-6500RPS.js
    ```

This will simulate multiple users creating accounts and making transfers, helping to validate the performance and resilience of the system under load.


         /\      Grafana   /‾‾/  
    /\  /  \     |\  __   /  /   
   /  \/    \    | |/ /  /   ‾‾\ 
  /          \   |   (  |  (‾)  |
 / __________ \  |_|\_\  \_____/ 

     execution: local
        script: load-test-6500RPS.js
        output: -

     scenarios: (100.00%) 1 scenario, 5000 max VUs, 5m30s max duration (incl. graceful stop):
              * transfer_stress_test: 5000.00 iterations/s for 5m0s (maxVUs: 1000-5000, gracefulStop: 30s)



  █ THRESHOLDS 

    http_req_duration
    ✓ 'p(95)<500' p(95)=66.82ms

    http_req_failed
    ✓ 'rate<0.01' rate=0.00%


  █ TOTAL RESULTS 

    checks_total.......: 1492391 4974.584246/s
    checks_succeeded...: 100.00% 1492391 out of 1492391
    checks_failed......: 0.00%   0 out of 1492391

    ✓ status is 202

    HTTP
    http_req_duration..............: avg=14.21ms min=1.24ms med=1.76ms max=1.39s p(90)=6.03ms p(95)=66.82ms
      { expected_response:true }...: avg=14.21ms min=1.24ms med=1.76ms max=1.39s p(90)=6.03ms p(95)=66.82ms
    http_req_failed................: 0.00%   0 out of 1492391
    http_reqs......................: 1492391 4974.584246/s

    EXECUTION
    dropped_iterations.............: 7610    25.366399/s
    iteration_duration.............: avg=14.37ms min=1.35ms med=1.9ms  max=1.39s p(90)=6.28ms p(95)=67.14ms
    iterations.....................: 1492391 4974.584246/s
    vus............................: 10      min=7            max=1784
    vus_max........................: 2576    min=1077         max=2576

    NETWORK
    data_received..................: 109 MB  364 kB/s
    data_sent......................: 386 MB  1.3 MB/s




running (5m00.0s), 0000/2576 VUs, 1492391 complete and 0 interrupted iterations
transfer_stress_test ✓ [======================================] 0000/2576 VUs  5m0s  5000.00 iters/s


---

## 📂 Project Structure

```
.
├── src
│   ├── main
│   │   ├── java/com/astropay
│   │   │   ├── application         # Use Cases, DTOs, Services (Application Layer)
│   │   │   ├── domain              # Entities, Business Rules (Domain Layer)
│   │   │   └── infrastructure      # Configurations, Adapters (Infrastructure Layer)
│   │   └── resources
│   │       ├── application.properties
│   │       └── db/migration        # Flyway scripts
│   └── test                      # Unit and integration tests
├── docker-compose.yml              # Local infrastructure orchestration
├── insomnia_collection_ledger.json # Insomnia request collection
├── pom.xml                         # Project dependencies and build
├── README.md                       # This file
└── Why was it used?                # Detailed architectural decisions
```

## 🔄 Important Business Flows

For a detailed explanation of the architectural decisions and flows, please refer to **[Why was it used?](Why%20was%20it%20used?)**.

### 1. User Creation

1.  `POST /users` is called.
2.  `UserServiceImpl` validates the data and saves a new `User`.
3.  Immediately, `UserServiceImpl` calls `AccountService.createAccountForUser` to create an associated account with a default initial balance.
4.  `AccountService` triggers an `AccountCreatedEvent` to Kafka.
5.  `AccountCreatedEventListener` consumes the event and (simulates) sending a welcome email.

### 2. Money Transfer

1.  `POST /transfers` is called.
2.  `TransferController` returns `202 Accepted` immediately.
3.  `TransferServiceImpl` **does not** execute the transfer. It creates an `OutboxEvent` and saves it to the `tb_outbox` table in the same transaction.
4.  `TransferEventScheduler` (running every 2 seconds) fetches events from `tb_outbox`.
5.  For each event, the scheduler:
    a. Creates a `Transaction` with `PENDING` status and saves it.
    b. Attempts to debit and credit the accounts.
    c. Updates the `Transaction` to `SUCCESS` or `FAILED`.
    d. Triggers a `TransactionEvent` to Kafka.
6.  `TransactionEventListener` consumes the event and (simulates) sending notification emails to the sender and receiver.
