# Hlool Agent Developer Guide

## Auth (access token only)

- Persist `accessToken` + `userId` in `~/.hlool-agent/config.json`
- Auto-claim relay sk- named `Hlool Agent` via `/api/token/` + `/api/token/:id/key`
- **Never accept manual sk- paste**; `/settings set apiKey` is blocked
- Before chat, `ShuaiApiAuth.prepareForChat()` ensures relay key is valid

## Commands

- `/login` — password or access token → activateSession
- `/console` — balance, models, groups, tokens CRUD, logs, topup, notice, pricing, aff

## Build

```bash
./gradlew jvmTest
./gradlew jvmFatJar
```

Env: `HLOOL_ACCESS_TOKEN`, `HLOOL_USER_ID`, `HLOOL_BASE_URL` (also `NEWAPI_*`).
