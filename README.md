# Solar-Miner-Node

**Important Notice:** SolarMiner™ is strictly protected by trademark law. Modifying, forking, or commercializing this software requires the complete removal of the SolarMiner name and logo. Please refer to the `TRADEMARK.md` file for details. This software is provided under the AGPLv3 license and includes a mandatory developer fee.

---

## Overview

SolarMiner Node is an advanced, microservices-based management system designed to dynamically synchronize cryptocurrency mining (ASIC, GPU, CPU) with the power production of photovoltaic systems. The system controls mining hardware in real-time based on the available solar surplus to maximize energy efficiency and profitability.

The architecture is modular and optimized for local operation (self-hosting) via Docker Compose.

## System Architecture & Components

The project is divided into several specialized microservices and modules to ensure maximum performance, scalability, and stability.

### 1. Main Application (`src` / `frontend`)
The main program serves as the control center of the system.
*   **Function:** Contains the backend for the business logic as well as the frontend for the user interface.
*   **Data Storage:** Utilizes a MariaDB for relational data and configurations, and an InfluxDB for high-resolution time-series data and metrics.
*   **Accessibility:** The web interface is exposed on port `8080` by default.

### 2. Core Service (`core`)
The core component for hardware communication.
*   **Function:** A high-performance binary responsible for lightning-fast, zero-latency communication with the miners.
*   **Roadmap:** This service is designed to continuously take over additional low-level functionalities and critical routing tasks over time.

### 3. Currency Rates Service (`currency-service`)
A specialized microservice for financial data.
*   **Function:** Collects and stores current exchange rates and blockchain data to guarantee accurate conversions and profitability calculations in the main program.
*   **Data Storage:** Features its own isolated MariaDB instance to prevent database bottlenecks.

### 4. Phoenixd Integration (`phoenixd`)
*   **Function:** The system integrates a full Lightning Network node based on ACINQ's `phoenixd`. This enables the direct processing of payments and transactions via the Bitcoin Lightning Network.

### 5. PC-Agent (`pc-agent`)
*   **Status:** Work in progress.
*   **Function:** A native client that will in the future manage CPU and GPU mining workloads on desktop operating systems (Windows, Linux, macOS) in conjunction with the SolarMiner Node.

## Deployment & Installation

The application is pre-configured for production deployment via container orchestration.

### Prerequisites
*   Docker
*   Docker Compose

### Configuration
The system requires a `.env` file in the root directory to assign critical environment variables. The following variables must be defined:
*   `MYSQL_PASSWORD`
*   `INFLUXDB_ADMIN_TOKEN`
*   `DOCKER_INFLUXDB_INIT_USERNAME`
*   `DOCKER_INFLUXDB_INIT_PASSWORD`
*   `CURRENCY_DB_PASSWORD`

### License & Contributing
This project is licensed under the GNU Affero General Public License v3.0 (AGPLv3).

Please note that code contributions (Pull Requests) require agreement to our Contributor License Agreement (CLA). Any commercial use or distribution of modified versions is subject to strict trademark restrictions (see TRADEMARK.md).
