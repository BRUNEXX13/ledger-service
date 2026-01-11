# 📘 Ledger Service: Technical Architecture and Engineering Report

## 1. System Overview
The **Ledger Service** is a high-performance accounting system designed to manage digital accounts and process financial transactions at scale. It adopts a **"Safety First"** asynchronous architecture, prioritizing Data Durability (ACID) while achieving high throughput via the **Transactional Outbox Pattern** and **Parallel Processing**.

## 2. Software Architecture
The system was built upon the pillars of **Hexagonal Architecture (Ports and Adapters)**.

*   **Core (Domain):** Business rules (Entities `Account`, `User`, `Transaction`) are pure and framework-agnostic.
*   **Ports:** Interfaces defining input contracts (Use Cases) and output contracts (Repositories, Publishers).
*   **Adapters:**
    *   *Driving (Input):* REST Controllers (Spring Web) and Kafka Listeners.
    *   *Driven (Output):* Spring Data JPA (Postgres), Kafka Producer, Redis Client.

### The "Transactional Outbox" Pattern
To ensure that *no financial transaction is lost*, the system does not publish to Kafka directly during the HTTP request.

1.  **Ingestion:** The transfer request is serialized and saved in the `tb_outbox_event` table.
2.  **Atomicity:** This save happens within a database transaction. If the DB confirms, the data is safe on disk.
3.  **Processing:** A background scheduler reads these events and executes the business logic.

## 3. Technology Stack & Tuning

*   **Language:** Java 21.
    *   **Virtual Threads (Project Loom):** Enabled to handle thousands of concurrent HTTP connections with minimal overhead, replacing the traditional thread-per-request model.
*   **Framework:** Spring Boot 3.2.
*   **Database:** PostgreSQL 15.
    *   **Tuning:** `synchronous_commit=on` (Safety), `autovacuum` optimized for high-churn tables, `shared_buffers=1GB`.
    *   **Concurrency:** Use of `SELECT ... FOR UPDATE SKIP LOCKED` allows multiple threads/instances to consume the Outbox queue without contention.
*   **Messaging:** Apache Kafka.
*   **Cache:** Redis (for read-heavy data).

## 4. Critical Business Flows

### A. Financial Transfer (High-Throughput)
This flow is optimized for safety and scale:

1.  **Ingestion (API):**
    *   Client sends `POST /transfers`.
    *   System persists `TransferRequested` event to DB.
    *   Returns **202 Accepted**. Latency: ~20ms.
2.  **Processing (Scheduler):**
    *   **Parallel Workers:** 8 threads run in parallel.
    *   **Batch Fetch:** Each thread fetches a batch of 200 events using `SKIP LOCKED`.
    *   **Execution:**
        *   Locks accounts (ordered by ID to prevent Deadlocks).
        *   Validates balance.
        *   Updates balances and creates `Transaction` record.
        *   Marks Outbox event as `PROCESSED`.
3.  **Notification:**
    *   `TransactionCompleted` events are pushed to Kafka.
    *   Consumers send emails/notifications.

## 5. Performance and Scalability

Based on load tests performed with **k6** on a local environment:

*   **Throughput:** **6,500 Requests Per Second (RPS)** sustained.
*   **Latency (p95):** **~130ms**.
*   **Reliability:** 1.9 Million requests processed with **0% error**.
*   **Scalability:** The architecture supports horizontal scaling. Adding more application instances linearly increases the processing power due to the non-blocking nature of `SKIP LOCKED`.

### Bottleneck Analysis
*   **Ingestion:** Limited by Database Disk I/O (IOPS) due to synchronous writes.
*   **Processing:** Limited by Database CPU and Lock Contention on "hot" accounts.

## 6. Security and Integrity

*   **Idempotency:** The `tb_transaction` table has a `UNIQUE` constraint on the `idempotency_key` column. Duplicate requests are rejected at the database level.
*   **Numerical Precision:** Use of `BigDecimal` and PostgreSQL `NUMERIC` types.
*   **Isolation:** Pessimistic Locking (`PESSIMISTIC_WRITE`) ensures that no two transactions modify the same account simultaneously.

## 7. Conclusion

The **Ledger Service** demonstrates that it is possible to build a Java-based system that rivals high-frequency trading platforms in terms of throughput, while maintaining the strict safety guarantees required for banking software. The use of **Virtual Threads** and **Postgres Advanced Locking** features were key enablers for this performance.
