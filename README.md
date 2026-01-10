# Ledger Service - High-Performance Accounting System

This project implements a robust, scalable, and secure Ledger service to manage user accounts and financial transactions. It is designed following the best practices of microservices architecture, with a focus on **data integrity (ACID)**, **high throughput**, and **resilience**.

## 🚀 Technologies and Tools

The project uses a modern and complete technology stack:

### Backend & Frameworks
*   **Java 21:** Base language, leveraging **Virtual Threads (Project Loom)** for high-concurrency I/O.
*   **Spring Boot 3.2:** Main framework for dependency injection, configuration, and execution.
*   **Spring Data JPA / Hibernate:** Persistence layer and ORM.
*   **Spring Web:** Building the RESTful API.
*   **Spring HATEOAS:** Implementation of hypermedia in the API.
*   **Flyway:** Database migration and versioning management.

### Infrastructure & Data
*   **PostgreSQL 15:** Main relational database, tuned for high write throughput (`synchronous_commit=on` for safety).
*   **Redis:** Distributed cache for high performance in reads (Users/Accounts).
*   **Apache Kafka:** Event streaming platform for asynchronous notifications.
*   **Docker & Docker Compose:** Containerization and orchestration of the development environment.

### Observability & Monitoring
*   **Grafana:** Real-time visualization of metrics and dashboards.
*   **Prometheus:** Collection and storage of application metrics.
*   **Micrometer:** Metrics facade for application instrumentation.

### Testing & Quality
*   **JUnit 5:** Unit testing framework.
*   **Mockito:** Mocking framework for isolated tests.
*   **Testcontainers:** Integration tests with real containers (Postgres, Kafka).
*   **k6:** Tool for load and performance testing.

---

## 🏗️ Architecture: "Safety First"

The system follows a **Hexagonal Architecture (Ports and Adapters)** combined with the **Transactional Outbox Pattern**.

### Key Architectural Decisions:
1.  **Safety First:** Unlike "fire-and-forget" systems, this Ledger prioritizes data durability. Every transfer request is synchronously persisted to the PostgreSQL disk before confirming to the client.
2.  **Asynchronous Processing:** While ingestion is synchronous (to disk), processing is asynchronous. This decouples the API from the heavy lifting of locking accounts and updating balances.
3.  **Concurrency Control:** Uses `SELECT ... FOR UPDATE SKIP LOCKED` to allow multiple scheduler threads (or instances) to process events in parallel without contention.
4.  **Virtual Threads:** Enabled to handle thousands of concurrent HTTP connections with minimal memory footprint.

### Key Components:
-   **REST API:** Accepts requests and persists them to the `tb_outbox_event` table.
-   **TransferEventScheduler:** A background worker that reads from the Outbox, executes the business logic (debit/credit), and updates the transaction status.
-   **Idempotency:** Guaranteed via unique constraints on `idempotency_key` in the database.

---

## ✨ Main Features

-   **User Management:** Complete CRUD for users.
-   **Account Management:**
    -   Automatic account creation when registering a new user.
    -   Querying account balance and details.
    -   Deactivation of accounts.
-   **Financial Transfers:**
    -   Endpoint to request transfers between accounts.
    -   **High Throughput:** Supports **6,500+ RPS** on a single node.
    -   **Zero Data Loss:** ACID guarantees on ingestion.
    -   Email notification (simulated) via Kafka consumers.

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

### 3. Accessing Auxiliary Services

-   **API Documentation (Swagger):** `http://localhost:8082/api/v1/swagger-ui.html`
-   **Grafana:** `http://localhost:3000` (login: `admin`/`admin`)
-   **Prometheus:** `http://localhost:9090`

### 4. Importing Requests (Insomnia)

To facilitate manual API testing, an Insomnia collection file is included at the root of the project: `insomnia_collection.yaml`.

---

## ✅ Tests & Performance

### Unit and Integration Tests

To run all unit and integration tests (100% coverage on critical paths):

```sh
./mvnw test
```

### Load Testing (k6)

The project includes a calibrated load test script.

1.  **Install k6:** Follow the instructions at `k6.io`.
2.  **Run the test:**

    ```sh
    k6 run k6/stress-test-6k.js
    ```

**Latest Benchmark Results (Local Environment):**

```
     scenarios: (100.00%) 1 scenario, 5000 max VUs, 5m30s max duration:
              * stress_test_6k: 6500.00 iterations/s for 5m0s

  █ TOTAL RESULTS 

    checks_succeeded...: 100.00% 1945121 out of 1945121
    checks_failed......: 0.00%   0 out of 1945121

    HTTP
    http_req_duration..: p(95)=129.94ms
    http_req_failed....: 0.00%
    http_reqs..........: 6483.36896/s
```

*   **Throughput:** ~6,500 Requests/sec
*   **Latency (p95):** ~130ms
*   **Errors:** 0%

---

## 🔄 Business Flows

### 1. User Creation
1.  `POST /users` is called.
2.  `UserServiceImpl` saves a new `User`.
3.  `AccountService` creates an associated `Account`.
4.  An `AccountCreatedEvent` is saved to the Outbox.
5.  `OutboxEventScheduler` publishes the event to Kafka.

### 2. Money Transfer (High-Performance Flow)
1.  `POST /transfers` is called.
2.  `TransferController` validates the request.
3.  `TransferServiceImpl` persists a `TransferRequested` event to `tb_outbox_event` (ACID transaction).
4.  API returns `202 Accepted`.
5.  **Async Worker (`TransferEventScheduler`):**
    *   Fetches unprocessed events using `SKIP LOCKED`.
    *   Locks sender and receiver accounts (ordered by ID to avoid deadlocks).
    *   Executes debit/credit.
    *   Updates transaction status to `SUCCESS` or `FAILED`.
6.  **Notification:** `TransactionCompleted` event is published to Kafka for email sending.
