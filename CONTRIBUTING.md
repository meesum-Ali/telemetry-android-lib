# Contributing to Telemetry Android Library

Thank you for considering contributing to the Telemetry Android Library! We welcome all contributions, whether they're bug reports, feature requests, documentation improvements, or code changes.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Coding Standards](#coding-standards)
- [Pull Request Process](#pull-request-process)
- [Reporting Bugs](#reporting-bugs)
- [Feature Requests](#feature-requests)
- [Code Review Process](#code-review-process)
- [Release Process](#release-process)
- [Community](#community)

## Code of Conduct

This project and everyone participating in it is governed by our [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report any unacceptable behavior to the project maintainers.

## Getting Started

1. **Fork** the repository on GitHub
2. **Clone** your fork locally
   ```bash
   git clone https://github.com/meesum-ali/telemetry-android-lib.git
   cd telemetry-android-lib
   ```
3. **Add the upstream repository** as a remote
   ```bash
   git remote add upstream https://github.com/meesum-ali/telemetry-android-lib.git
   ```
4. **Create a branch** for your changes
   ```bash
   git checkout -b feature/your-feature-name
   ```

## Development Setup

### Prerequisites

- Android Studio Giraffe (2022.3.1) or later
- JDK 17 or later
- Android SDK 34
- Gradle 8.0+

### Building the Project

1. Open the project in Android Studio
2. Sync the project with Gradle files
3. Build the project:
   ```bash
   ./gradlew build
   ```

### Running Tests

Run unit tests:
```bash
./gradlew test
```

Run instrumented tests:
```bash
./gradlew connectedAndroidTest
```

### Code Style

We use [ktlint](https://ktlint.github.io/) for Kotlin code style enforcement. Before committing, please run:

```bash
./gradlew ktlintFormat
```

## Coding Standards

### Kotlin

- Follow the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use 4 spaces for indentation (no tabs)
- Use `camelCase` for variable and function names
- Use `PascalCase` for class and interface names
- Use `UPPER_SNAKE_CASE` for constants
- Always explicitly specify return types for public functions
- Prefer `val` over `var`
- Use `?` for nullable types
- Use `!!` only when you can guarantee the value is not null

### Documentation

- Document all public APIs using KDoc
- Include examples for complex functions
- Keep documentation up-to-date with code changes
- Document any breaking changes in the PR description

## Pull Request Process

1. Ensure any install or build dependencies are removed before the end of the layer when doing a build
2. Update the README.md with details of changes to the interface, this includes new environment variables, exposed ports, useful file locations and container parameters
3. Increase the version numbers in any examples files and the README.md to the new version that this Pull Request would represent. The versioning scheme we use is [SemVer](http://semver.org/)
4. You may merge the Pull Request in once you have the sign-off of two other developers, or if you do not have permission to do that, you may request the second reviewer to merge it for you

## Reporting Bugs

Bugs are tracked as [GitHub issues](https://github.com/meesum-ali/telemetry-android-lib/issues). When creating a bug report, please include:

1. **A clear title and description**
2. **Steps to reproduce** the issue
3. **Expected behavior**
4. **Actual behavior**
5. **Screenshots** (if applicable)
6. **Device information**:
   - Device model
   - Android version
   - Library version

## Feature Requests

We welcome feature requests! Please submit them as [GitHub issues](https://github.com/meesum-ali/telemetry-android-lib/issues) with:

1. A clear title and description
2. The problem you're trying to solve
3. Any alternative solutions you've considered
4. Additional context or examples

## Code Review Process

1. All code submissions require review by at least one maintainer
2. We use GitHub's pull request review feature
3. All PRs must pass all CI checks before merging
4. At least one approval is required before merging
5. The PR author is responsible for addressing all review comments

## Release Process

1. Update the version in `build.gradle`
2. Update `CHANGELOG.md` with the new version and changes
3. Create a release tag: `git tag -a v1.0.0 -m "Version 1.0.0"`
4. Push the tag: `git push origin v1.0.0`
5. Create a new release on GitHub with the release notes
6. Publish to Maven Central (for maintainers only)

## Community

- Join our [Slack/Discord channel]()
- Follow us on [Twitter]()
- Check out our [blog]() for updates and tutorials

## License

By contributing, you agree that your contributions will be licensed under its [MIT License](LICENSE).
