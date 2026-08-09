# Cocacode Developer Guide

CocaCode — 一个用 Kotlin 重写的 Claude Code 式 AI 编程助手（Kotlin Multiplatform）。

## Build Commands

```bash
./gradlew build              # 完整构建（jvm + mingwX64 + 测试），当前全绿
./gradlew jvmFatJar          # 打包 fat JAR（build/libs/cocacode-<ver>.jar）
./gradlew jvmTest            # 运行 JVM 测试（56 个，全部通过）
./gradlew run --args="-i"    # 交互模式
./gradlew run --args="-p '你的问题'"   # 单次 prompt
./gradlew run --args="--resume"        # 恢复最近会话
```

## 技术栈

- **语言**: Kotlin 2.3.20, JVM
- **构建**: Gradle 9.x (build.gradle.kts)
- **KMP 目标**: `jvm` + `mingwX64`
- **HTTP**: Ktor (CIO) · **终端**: JLine · **序列化**: kotlinx-serialization + Gson
- **版本号**: 由 BuildKonfig 编译期生成（改 `build.gradle.kts` 的 `version` 即可全局生效）

## 源码结构

```
src/
├── commonMain/        # expect 声明（Platform, generateUuid 等）
├── jvmMain/kotlin/rj/cocacode/
│   ├── Main.kt        # CLI 入口（-v/-i/-p/--resume）
│   ├── repl/          # REPL 主循环（状态栏、type-ahead、Ctrl+C 提示）
│   ├── engine/        # QueryEngine —— 流式工具循环（核心）
│   ├── services/api/  # ApiClient(真实 HTTP)、RequestBuilder、ResponseParser、StreamAccumulator
│   ├── config/        # ApiConfig（baseUrl/apiType/apiKey + BuildKonfig 版本）
│   ├── tools/         # 工具：Bash/Read/Write/Edit/Glob/Grep/Task*/SendMessage/TeamCreate
│   ├── agents/        # 子代理 + teammates（SubAgentEngine, TeammateManager, AgentLoader）
│   ├── commands/      # /help /settings /tools /clear /resume /exit /teammate
│   ├── ui/            # TerminalUI, Ansi, StreamRenderer, StatusBar, MarkdownRenderer, SessionPicker, TypeAhead
│   ├── utils/         # Shell, SessionStorage, ConfigManager 等
│   └── types/         # Message, MessageType, Attachment, Metrics, ApiError
├── jvmTest/           # 测试
└── mingwX64Main/      # Windows 入口（版本输出）
```

## 配置

用户配置 `~/.cocacode/config.json`（镜像 models.json 风格，简洁版）：

```json
{
  "version": 1,
  "apiType": "chat",                  // "chat"(OpenAI兼容) 或 "messages"(Anthropic)
  "baseUrl": "https://opencode.ai/zen/go",
  "model": "deepseek-v4-flash",
  "apiKey": "sk-...",
  "maxTokens": 384000,
  "maxThinkingTokens": 16000
}
```

环境变量（覆盖 config）：`COCA_API_KEY` / `COCA_BASE_URL` / `COCA_MODEL` / `COCA_API_TYPE` / `COCA_MAX_TOKENS` / `COCA_MAX_THINKING_TOKENS`。

**`apiType` 决定**：端点路径（`v1/messages` vs `v1/chat/completions`）、认证头（`x-api-key` vs `Bearer`）、请求格式。不声明时按 host 推断（anthropic → messages，否则 chat）。

## 核心功能

- **流式输出**：thinking + 正文流式渲染（`∴ Thinking…` 淡灰、markdown 逐行、状态栏 `✽ Musing… (时间 · ↓ tokens)`）
- **思考**：默认启用（thinkingBudget 16000），始终展开
- **工具调用循环**：模型 tool_use → 执行 → 回传 → 继续，最多 10 轮
- **markdown 渲染**：加粗/代码块/标题/列表/链接/行内代码
- **子代理**：`Task` 工具（带 `name` 即成为 teammate）
- **teammates 模式**：默认启用，`TeamCreate` 全局开关，`/teammate` 命令管理，SendMessage 通信
- **session 持久化**：自动保存到 `~/.cocacode/sessions/`，`--resume`/`/resume`（交互菜单）恢复，`/clear` 新会话
- **状态栏 + type-ahead**：处理中可输入但 Enter 不发送，完成后预填

## 配置目录

- 用户配置：`~/.cocacode/config.json`
- 会话：`~/.cocacode/sessions/`
- 环境变量：`COCACODE_CONFIG_DIR`（默认 `~/.cocacode`）、`COCA_` 前缀
