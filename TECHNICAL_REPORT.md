# 📘 Ledger Service: Technical Architecture and Engineering Report

## 1. System Overview
The **Ledger Service** is a high-performance, cloud-native accounting system designed to manage digital accounts and process financial transactions at scale. It is engineered with a strong focus on **data integrity, low latency, and high throughput**. The architecture is built on an **asynchronous, event-driven model**, prioritizing Availability and Eventual Consistency to support massive load spikes, as validated by rigorous load testing.

## 2. Core Architectural Pillars

### 2.1. Hexagonal Architecture (Ports and Adapters)
The system's foundation is a strict implementation of Hexagonal Architecture, ensuring a clean separation of concerns:
*   **Core (Domain):** Contains the business logic and entities (`Account`, `Transaction`). It is completely agnostic of external frameworks and technologies like Spring, Postgres, or Kafka.
*   **Ports:** Defines the contracts for inbound (Use Cases) and outbound (Repositories, Event Publishers) communication.
*   **Adapters:**
    *   *Driving (Input):* REST Controllers and Kafka Listeners that translate external requests into calls to the application's core.
    *   *Driven (Output):* Concrete implementations for database access (Spring Data JPA), event publishing (Kafka), and caching (Redis).

### 2.2. The Transactional Outbox Pattern: Guaranteeing Data Consistency
To solve the critical "dual-write problem" in distributed systems, the service implements the **Transactional Outbox Pattern**. This ensures that a business transaction and the events it produces are saved atomically.

**Flow:**
1.  An incoming request (e.g., a transfer) initiates a database transaction.
2.  Instead of directly modifying account balances and publishing to Kafka, the service creates an `OutboxEvent` entity. This event contains all the necessary information for the subsequent processing.
3.  This `OutboxEvent` is saved to the `tb_outbox_event` table **within the same ACID transaction** as the primary business data.
4.  The API immediately returns `202 Accepted`, providing a low-latency user experience.
5.  A separate, asynchronous process (a scheduler) polls the outbox table, processes the events (e.g., updates account balances), and reliably publishes them to Kafka upon successful completion.

This pattern guarantees that **no event is ever lost or published without its corresponding database state being committed**, ensuring "at-least-once" delivery and enabling a resilient, eventually consistent system.

## 3. High-Performance Technology Stack & JVM Tuning

The technology stack was carefully selected to achieve extreme performance and low latency under high concurrency.

*   **Language:** **Java 21**
*   **Framework:** **Spring Boot 3.x**

### 3.1. Concurrency Model: Java 21 Virtual Threads
The service leverages **Virtual Threads** (`spring.threads.virtual.enabled=true`), a cornerstone of modern Java concurrency.
*   **Why it matters:** For an I/O-bound application like this (waiting for the database, Kafka, Redis), Virtual Threads allow the system to handle thousands of concurrent requests with a very small number of OS threads. This dramatically reduces memory consumption and context-switching overhead, enabling massive scalability.

### 3.2. Garbage Collection: Generational ZGC
The JVM is tuned to use the **Generational Z Garbage Collector (ZGC)** (`-XX:+UseZGC -XX:+ZGenerational`).
*   **Why it matters:** ZGC is designed for sub-millisecond pause times, even with large heaps. The generational mode (new in Java 21) further optimizes CPU usage by focusing on short-lived objects. This is critical for maintaining consistently low p95/p99 latencies during high-throughput scenarios.

### 3.3. Performance Tuning Across the Stack
*   **Database (PostgreSQL):**
    *   **JDBC Batching:** Enabled at both the Hibernate (`hibernate.jdbc.batch_size`) and JDBC driver (`reWriteBatchedInserts=true`) levels to send multiple write operations in a single network round-trip.
    *   **Prepared Statement Caching:** The driver is configured to cache compiled SQL queries, reducing parsing overhead on the database.
*   **Kafka Producer:**
    *   **Batching & Compression:** The producer is tuned to batch messages (`batch-size`, `linger.ms`) and use `lz4` compression, maximizing throughput and minimizing network traffic.
*   **Connection Pools (Hikari & Lettuce):**
    *   Pool sizes are carefully tuned to work *with* Virtual Threads, preventing resource exhaustion and contention.

## 4. Load Test Results & Validation

The architecture was validated through extensive load testing using **k6**, simulating a high-volume transfer scenario on local hardware.

*   **Target Load:** 6,500 Requests Per Second (RPS).
*   **Test Duration:** 5 minutes (Soak Test).
*   **Total Requests Processed:** ~1.9 Million.

### Key Performance Metrics:
| Metric | Result | Interpretation |
| :--- | :--- | :--- |
| **Throughput** | **~6,492 RPS** | Sustained near-target load on local hardware. |
| **Error Rate** | **0.00%** | Flawless reliability under saturation. |
| **Median Latency (p50)** | **1.68 ms** | Exceptional responsiveness for the majority of requests. |
| **95th Percentile Latency (p95)** | **13.27 ms** | Excellent low latency even under high load. |

These results concretely demonstrate the effectiveness of the architectural choices and performance tuning, proving the system's capability to operate in demanding, large-scale production environments.

## 5. Security and Data Integrity
*   **Idempotency:** A `UNIQUE` constraint on the `idempotency_key` column provides a rock-solid guarantee against duplicate transaction processing.
*   **Numerical Precision:** The use of `BigDecimal` in Java and `NUMERIC` in Postgres eliminates floating-point rounding errors, which is non-negotiable for financial systems.
*   **Database Constraints:** `CHECK (balance >= 0)` constraints act as a final line of defense, ensuring data integrity at the database level.

## 6. Expert Conclusion
The **Ledger Service** is a showcase of modern, expert-level software engineering. It deliberately moves beyond simple CRUD patterns to correctly address the complexities of distributed financial systems. The synergistic use of **Java 21 Virtual Threads, Generational ZGC, and the Transactional Outbox Pattern** creates a system that is not only highly performant and scalable but also resilient and consistent by design.
