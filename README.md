# Serverless Apparel Price Tracker 
A full-stack, serverless application that tracks apparel prices across e-commerce platforms. This project utilizes a hybrid "Omni-Channel" architecture, allowing users to interact with the tracker either through a locally hosted Command Line Interface (CLI) or a live Cloud Web Dashboard, with both environments seamlessly synchronizing data.
 

## Tech Stack
* **Frontend:** HTML5, CSS3, Vanilla JavaScript, Fetch API
* **Backend Engine:** Java 17, Maven, Selenium WebDriver, Gson
* **Cloud Infrastructure (AWS):
  * **S3:** NoSQL JSON Object Storage
  * **API Gateway:** RESTful routing and CORS management
  * **Lambda:** Serverless data fetching and processing
  * **SES:** Transactional email pipeline


## Architecture Overview
* **Dual-Brain Synchronization:** Items added via the local Java terminal instantly push payloads to the AWS Cloud, and vice versa.
* **Serverless API:** The frontend communicates with a decoupled AWS Lambda backend via API Gateway, bypassing CORS restrictions.
* **Automated Cloud Alerts:** Price drops trigger automated transactional emails routed through Amazon Simple Email Service (SES).
* **Headless Scraping:** Powered by a background Java/Selenium worker that evaluates DOM elements dynamically.

## Quick Start: Local Testing Mode (Default)
The repository is configured to run locally without requiring AWS credentials or cloud provisioning.

### Prerequisites
* Java 17+
* Apache Maven
* ChromeDriver with the same version as chrome
* Google Chrome (for Selenium WebDriver)
* VS Code with the "Live Server" extension

## Folder Structure
```text
apparel-tracker/
├── pom.xml                           # Maven dependencies (Selenium, AWS SDK, Gson)
├── src/main/java/
│   ├── Scraper.java                  # Automation engine (Local Execution)
│   └── ApparelTrackerBackend.java    # Serverless backend logic (AWS Lambda)
└── frontend/                         # Frontend Dashboard UI
    ├── index.html                    
    └── app.js                        # Houses both Local and AWS fetch logic
    └── Style.css                     


TO BUILD THE PROJECT : mvn clean compile
TO EXECUTE THE PROJECT : mvn exec:java
