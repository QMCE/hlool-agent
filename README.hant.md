# Hlool Agent

Hlool Agent — 面向 [SHUAI API](https://api.shuaiapi.com) 的 AI 編程助手

[简体中文](README.md) | [English](README.en.md)

### 簡介

Hlool Agent 針對 **帥 API**（`api.shuaiapi.com`）：控制台裡的 **一把 sk- Key** 即可使用與網頁版相同的 OpenAI 相容模型面。

### 快速開始

```bash
./gradlew jvmFatJar
java -jar build/libs/hlool-agent-*.jar -i
```

```text
/login              # 開啟登入頁後貼上 sk-
/login sk-xxxx      # 校驗並寫入設定
/login oauth        # 瀏覽器 GitHub / LinuxDo / Passkey
/login password     # 帳密登入並自動建立令牌
```

設定檔：`~/.hlool-agent/config.json`（相容舊版 `~/.cocacode/`）。

環境變數：`HLOOL_API_KEY` / `HLOOL_BASE_URL` / `HLOOL_MODEL` / `HLOOL_API_TYPE`。

### 授權

BSD 3-Clause，見 [LICENSE](LICENSE)。
