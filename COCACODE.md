# Hlool Agent Developer Guide

Hlool Agent — Kotlin Multiplatform AI coding assistant optimized for SHUAI API (`api.shuaiapi.com`).

## Build Commands

```bash
./gradlew build              # full build (jvm + mingwX64 + tests)
./gradlew jvmFatJar          # fat JAR → build/libs/hlool-agent-<ver>.jar
./gradlew jvmTest            # JVM tests
./gradlew run --args="-i"    # interactive
./gradlew run --args="-p 'hi'"
./gradlew run --args="--resume"
```

## Stack

- Kotlin 2.3.20, JVM + mingwX64
- Gradle 9.x · Ktor · JLine · kotlinx-serialization + Gson
- Version via BuildKonfig (`APP_NAME` = `hlool-agent`)

## Auth / SHUAI

`ShuaiApiAuth` + `/login`:

1. **sk key** — validate `GET /v1/models` with Bearer, save to config (primary “1 key” path).
2. **oauth/browser** — open `/login` + `/console/token` (GitHub / LinuxDo / Passkey on the gateway).
3. **password** — `POST /api/user/login` → create unlimited token → `GET /api/token/:id/key`.

Defaults: `baseUrl=https://api.shuaiapi.com`, `apiType=chat`.

## Config

`~/.hlool-agent/config.json` (legacy `~/.cocacode/` still read).

Env: `HLOOL_*` preferred; `COCA_*` / `ANTHROPIC_API_KEY` still accepted.
`HLOOL_CONFIG_DIR` overrides the home directory.

## Layout

```
src/jvmMain/kotlin/rj/cocacode/
├── Main.kt
├── services/oauth/ShuaiApiAuth.kt
├── constants/Product.kt          # brand + SHUAI URLs
├── utils/AppPaths.kt             # ~/.hlool-agent
├── engine/ · repl/ · tools/ · …
```

Kotlin package `rj.cocacode` is kept for source stability; product name is **Hlool Agent**.
