# Cocacode Developer Guide

## Build Commands

```bash
./gradlew build    # Build the project
./gradlew run     # Run the application
./gradlew test    # Run tests
```

## Architecture

- **Entry point**: `src/main/kotlin/rj/cocacode/Main.kt`
- **Language**: Kotlin 2.x, JVM 25
- **Build system**: Gradle (build.gradle.kts)
- **Version**: v0.3.0

## Key Directories

| Directory | Purpose |
|----------|---------|
| `src/main/kotlin/rj/cocacode/` | Main source code |
| `src/main/kotlin/rj/cocacode/query/` | Query engine (incomplete, many missing classes) |
| `src/main/kotlin/rj/cocacode/tools/` | Tool implementations |
| `src/main/kotlin/rj/cocacode/hooks/` | Hook system |
| `src/main/kotlin/rj/cocacode/services/` | External services |

## Current Issues

1. Build fails - many compilation errors in query/ directory (missing classes like `ToolExecutionResult`, `Metrics`, `ApiError`, `AbortController`)
2. MCP (Model Context Protocol) files have broken code

## Config Locations

- User settings: `~/.cocacode/settings.json`
- Project settings: `{project}/.cocacode/settings.json`

Environment variables: Use `COCACODE_` prefix (fallback to `COCA_` for compatibility).