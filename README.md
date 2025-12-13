astro-pay
A Ledger Service for user's

The Double-Entry Ledger

AstroPay is building a new neo-bank.

You need to build the core Ledger Service. This system is the "source of truth" for all user balances.

Requirement: Design a system that handles money transfers between two users within your platform (internal transfers).

Scale: 1,000 transactions per second (TPS) at peak.

Durability: ACID compliance is non-negotiable. Money cannot be created or destroyed. Connectivity: The system must emit events when a transaction completes so that downstream services (Notifications, Fraud Detection, Rewards) can react.

Deliverable: A high-level design document (diagrams + 1-2 page explanation) and a database schema. No full implementation is required, but pseudo-code for the critical "move money" function is encouraged.