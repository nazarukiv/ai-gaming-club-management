# 3. System Architecture

## 1. Overview

The project will use a Layered Monolith architecture. This architecture is planned and is not implemented yet.

All backend parts will be in one Spring Boot application, with a clear job for each layer.


## 2. Main Components

| Component | Job |
|-----------|-----|
| Controller | Receives requests from users |
| BookingService | Creates and cancels bookings |
| AvailabilityService | Checks free gaming stations |
| PricingService | Calculates booking prices |
| Repository | Works with the database |
| AI Assistant | Understands user messages and calls Java tools |
| PostgreSQL | Stores users, stations and bookings |

## 3. System Flow

```text
User
  |
  v
Frontend
  |
  v
Spring Boot Backend
  |
  +----> Controllers
  |         |
  |         v
  |      Services
  |         |
  |         v
  |      Repository
  |         |
  |         v
  |      PostgreSQL
  |
  +----> Spring AI
            |
            v
        Ollama LLM
            |
            v
        Java Tools
            |
            v
         Services
```
