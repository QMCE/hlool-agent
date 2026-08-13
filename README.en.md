# Hlool Agent

Hlool Agent — AI Coding Assistant for [SHUAI API](https://api.shuaiapi.com)

[简体中文](README.md) | [正體中文](README.hant.md)

### Overview

Hlool Agent is built for **SHUAI API** (`api.shuaiapi.com`). One console **sk-** key unlocks the same OpenAI-compatible model surface as the web app.

### Quick start

```bash
./gradlew jvmFatJar
java -jar build/libs/hlool-agent-*.jar -i
```

```text
/login              # open console login, then paste sk-
/login sk-xxxx      # validate + save
/login oauth        # browser GitHub / LinuxDo / Passkey
/login password     # username/password → create relay token
```

Config: `~/.hlool-agent/config.json` (falls back to legacy `~/.cocacode/`).

Env: `HLOOL_API_KEY` / `HLOOL_BASE_URL` / `HLOOL_MODEL` / `HLOOL_API_TYPE`.

Default base URL is `https://api.shuaiapi.com` with `apiType: chat` (Bearer auth).

### Auth model

SHUAI runs NewAPI. Browser OAuth is for the console; relay calls use a single `Authorization: Bearer sk-…` token — same contract as the site’s “one-click fill” clients.

### License

BSD 3-Clause — see [LICENSE](LICENSE).
