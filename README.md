# Hlool Agent

Hlool Agent — AI Coding Assistant for [SHUAI API](https://api.shuaiapi.com) | AI 编程助手

[English](README.en.md) | [正體中文](README.hant.md)

### 项目简介

Hlool Agent 是面向 **帅 API（api.shuaiapi.com）** 的 AI 编程助手：用控制台里的 **一把 sk- Key**，即可调用与网页版相同的 OpenAI 兼容全功能模型面（对话 / 多模型 / 工具循环等）。

### 主要特性

- **一键登录**：`/login` 支持粘贴 sk- Key、浏览器 OAuth（GitHub / LinuxDo / Passkey）、或账号密码自动建令牌
- **多 Agent 系统**：自定义 Agent、子代理与 teammate 协作
- **工具集**：`Bash` / `Read` / `Write` / `Edit` / `Glob` / `Grep` / `WebSearch`
- **REPL**：交互模式、会话恢复、Vim 键位、MCP / LSP

### 快速开始

#### 环境要求

- JDK 25+
- Kotlin 2.3+

#### 构建与运行

```bash
./gradlew jvmFatJar
java -jar build/libs/hlool-agent-*.jar -i
```

进入交互后：

```text
/login                # 打开帅 API 登录页，粘贴 sk- Key
/login sk-xxxx        # 直接写入并校验
/login oauth          # 浏览器 GitHub / LinuxDo
/login password       # 用户名密码 → 自动创建 relay token
```

配置文件默认在 `~/.hlool-agent/config.json`（兼容读取旧版 `~/.cocacode/`）。

环境变量（优先）：`HLOOL_API_KEY` / `HLOOL_BASE_URL` / `HLOOL_MODEL` / `HLOOL_API_TYPE`。

默认：

```json
{
  "baseUrl": "https://api.shuaiapi.com",
  "apiType": "chat",
  "apiKey": "sk-…",
  "model": "claude-sonnet-4-5-20250929"
}
```

### 鉴权说明（SHUAI / NewAPI）

帅 API 是 NewAPI 网关。网页端用 GitHub / LinuxDo / Passkey / 密码登录；真正调用模型走 **令牌（sk-）+ Bearer**，与 Cherry Studio / Lobe Chat 等「一键填入」客户端相同——**一把 Key 打通网页版可用的模型与协议面**。

### 许可证

BSD 3-Clause，详见 [LICENSE](LICENSE)。
