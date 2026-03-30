# Contributing to JMeter Web Insight Report

Thanks for your interest in contributing! This project is open to contributions of all kinds — bug fixes, features, documentation, and ideas.

## Getting Started

### Prerequisites

- Java 11+
- Maven 3.8+
- Apache JMeter 5.6.3+ (for manual testing)

### Build

```bash
mvn clean package -DskipTests
```

The plugin JAR is produced at `jmeter-web-insight-report/target/jmeter-web-insight-report-1.0.0.jar`.

### Run Tests

```bash
mvn test
```

### Test Manually

1. Copy the built JAR to JMeter's `lib/ext/` directory
2. Add the **Web Insight Report** listener to a test plan
3. Run the test — the report is generated when the test ends

## Project Structure

| Module | Description |
|--------|-------------|
| `jmeter-report-core` | Shared models, statistics engine, SLA evaluation |
| `jmeter-web-insight-report` | Web Insight Report generator plugin |

## Submitting Changes

1. Fork the repo and create a branch from `main`
2. Make your changes
3. Run `mvn test` and make sure tests pass
4. Submit a pull request with a clear description of what you changed and why

## Reporting Bugs

Use the **Bug Report** issue template. Include:
- JMeter version
- Java version
- Steps to reproduce
- Expected vs actual behavior

## Feature Requests

Use the **Feature Request** issue template. Describe the use case — understanding *why* helps prioritize.

## Code Style

- Follow existing code conventions in the project
- Keep dependencies minimal — this plugin runs inside JMeter's classpath
- The report HTML is a single self-contained file

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
