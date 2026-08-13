# Hlool Agent

Hlool Agent — AI Coding Assistant for [SHUAI API](https://api.shuaiapi.com)

[简体中文](README.md) | [正體中文](README.hant.md)

### One-key auth (Access Token)

**No manual sk- paste.** The credential is the console **Access Token**. On login, Hlool Agent auto-claims and persists a relay sk- named `Hlool Agent`.

```text
/login                      # password → persist access token + auto-claim sk-
/login access <token>       # paste Access Token from personal settings
/login oauth                # browser login, then paste Access Token (not sk-)
/console                    # full web console management
```

### Console

`/console balance|models|groups|tokens|logs|topup|notice|pricing|aff|claim` plus token CRUD.

### Build

```bash
./gradlew jvmFatJar && java -jar build/libs/hlool-agent-*.jar -i
```

### License

BSD 3-Clause — see [LICENSE](LICENSE).
