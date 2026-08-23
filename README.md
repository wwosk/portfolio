
# MyPaintings

  

  

> A modern, modular monolithic backend application built with **Java 25** and **Spring Boot 4**, bridging local artists and commercial venues - hotels. It combines a location-aware social feed, state-driven art reservations, real-time messaging via SSE, Redis UX locks, and a physical-to-digital marketplace workflow.

  

---

  

  

## Business Concept & User Flow

  

**MyPaintings** solves the problem of discovering local art and displaying it in physical spaces. The core business workflow consists of:

  

1.  **User Context & Security:** Application securely extracts and injects the ID of the currently authenticated user from contextHolder into incoming requests across most of the operations using **JWT (JSON Web Tokens)** and custom **AOP** aspects. Crucial endpoints are rate limited to ensure DOS protection (`auth` & `security` modules).

  

2.  **Location-Aware Discovery:** Artists publish posts featuring their artwork. The system uses cached geolocation data to locally promote artists within the main feed (`geolocation` & `feed` modules).

  

3.  **Venue Reservation (State Machine):** Commercial venues (such as hotels) browse the local feed and request a reservation for specific artwork, managed through a rigorous state machine (`reservation` module).

  

4.  **Collaboration & Real-Time Chat:** Once accepted, both parties negotiate terms via an internal messaging system featuring **Server-Sent Events (SSE)** for instant notifications (`conversation` module).

  

5.  **Placement:** The artwork is physically placed in the venue following a dedicated placement state machine (`painting/placement` module). Local storage manages physical image files, and a unique QR code can be linked to the physical frame on the frontend.

  

6.  **Purchase & Sale (Redis Locks):** Venue visitors scan the QR code to buy the artwork directly (`sale` module). To prevent checkout collisions and double-buys, temporary UX locks are managed via **Redis**.

  

---

  

  

## Architecture & Modular Design

  

The project follows a strict **Modular Monolith** architecture. Modules are kept independent and loosely coupled using an **Event-Driven Architecture (EDA)** with Spring's event publishers and handlers.

  

com.example.mypaintings
- auth # JWT authentication, storing credentials
- config # Global configurations
- conversation # Real-time messaging with Server-Sent Events (SSE)
- dashboard # Analytics and venue management views
- exception # Global exception handling & custom domain exceptions
- feed # Location-aware aggregated content feed
- geolocation # Spatial logic, caching, retry mechanism, and schedulers
- painting # Artwork management, local image storage, painting domain logic & placement state machine 
- reservation # Venue reservation workflow & state machine 
- sale # Purchase data processing, transaction handling & Redis UX locking
- security # Rate limiting annotations and protection against brute-force attacks on passwords
- user # User profiles (artists & hotels)
- validation # Custom jakarta validation annotations

  

## Key Architecture & Design Decisions

  

**Lightweight Domain State Machines:** Business workflows (reservations, artwork placement) are implemented using domain enum models paired with strict, transactional service-level guards (@Transactional). This design guarantees atomic state transitions without introducing unnecessary framework overhead.

  

**Event-Driven Decoupling:** Modules communicate asynchronously using Spring's ApplicationEventPublisher. Bounded contexts remain strictly decoupled by reacting to domain events (e.g., automatically initializing a conversation once a reservation is accepted).

  

**Atomic Concurrency Control (Redis TTL):** a priori UX checkout collisions and double-purchase attempts in the sale module are guarded via atomic Redis TTL locks (SETNX pattern). Locks expire automatically to prevent deadlocks if a checkout session is abandoned.

  

**Modern Java Domain Contracts**: Data Transfer Objects (DTOs) are built entirely on Java record types, ensuring thread-safety, immutability, and clean domain contracts across all module APIs.

  

**Pluggable File Storage:** Image file management is abstracted behind a dedicated storage service interface. Currently defaulting to local file storage for zero-dependency local runs, it is architected for seamless drop-in transition to cloud object storage (e.g. AWS S3) without changing domain logic.

  

  

**Tech Stack**

- Core Framework: Java 25, Spring Boot 4.x (WebMVC, Data JPA)
- Database & Caching: H2 Database, Redis (spring-boot-starter-data-redis)
- Containerization: Docker & Spring Boot Docker Compose Support (auto-provisions containerized Redis on app boot)
- Security & Auth: Spring Security, jwt, AOP (Aspect-Oriented Programming)
- Real-Time Communication: Server-Sent Events (SSE) for instant message delivery

  

**Resilience, Concurrency & Performance:**

- Redis UX Locks: Atomic distributed UX lock management during sales transactions
- Caffeine Cache & Bucket4j: API Rate Limiting protection
- Spring Retry & Schedulers: Robust handling of external/cached geolocation data
- Testing & Code Coverage: JUnit 5, Mockito, JaCoCo Maven Plugin


**API Documentation:**
Springdoc OpenAPI 3 (Swagger UI)

  
  
  

## Key Technical Highlights

**Decoupled Architecture (EDA):** Domain events decouple business boundaries so modules react independently without tight direct dependencies. Basically no domain leak problem.

**Complex Business State Machines:** Multi-step reservation and placement flows are guarded by transactional domain enums and service-layer validation.

**Distributed Concurrency Protection:** High-concurrency safety in the sale module prevents duplicate purchase attempts via atomic Redis locks.

**Zero-Setup Containerization:** Integrated spring-boot-docker-compose automatically handles dependent infrastructure (like Redis) during development.

**Real-Time Communication:** SSE implementation in the conversation module delivers instantaneous notifications without WebSocket connection overhead.

**Strict Code Quality & Coverage:** Measured via JaCoCo for clean metrics focused purely on business logic.

  
## Getting Started & Running

**Prerequisites**
- Java 25
- Docker / Podman (for automatic Redis provisioning via Docker Compose)

**App Execution**

git clone https://github.com/wwosk/portfolio

Change directory to mypaintings

mvnw spring-boot:run

Spring Boot Docker Compose will automatically start the required Redis container on startup.

  

**Access API Documentation:**

Once running, open your browser and navigate to:

Swagger UI: http://localhost:8080/swagger-ui.html


