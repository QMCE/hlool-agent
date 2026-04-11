

# CocaCode

CocaCode - AI Coding Assistant | AI 编程助手

[简体中文](#简体中文) | [English](#English)

---

## 简体中文

### 项目简介

CocaCode 是一个强大的 AI 编程助手，专为开发者设计，集成了多种 AI 模型、工具和扩展能力，帮助开发者更高效地完成编码任务。

### 主要特性

- **多 Agent 系统**：支持自定义 AI Agent，可加载多个 Agent 定义，灵活切换
- **丰富的工具集**：内置文件操作、搜索、代码编辑等工具
  - `Bash` - 执行 Shell 命令
  - `Read` - 读取文件内容
  - `Write` - 写入文件内容
  - `Edit` - 编辑文件内容
  - `Glob` - 按模式查找文件
  - `Grep` - 搜索文件内容
  - `WebSearch` - 搜索网络
- **REPL 交互模式**：支持交互式编程环境
- **扩展系统**：支持插件和扩展加载
- **技能系统**：内置技能加载器和执行器
- **记忆系统**：自动保存项目记忆和参考文档
- **上下文管理**：智能上下文管理，支持大规模代码库
- **调试器**：内置调试功能
- **差异引擎**：强大的代码diff对比能力
- **LSP 支持**：Language Server Protocol 集成
- **MCP 客户端**：Model Context Protocol 客户端支持
- **远程会话**：支持远程协作和会话管理
- **Keybindings**：可自定义键盘快捷键
- **Vim 模式**：完整的 Vim 编辑支持

### 技术栈

- **语言**: Kotlin
- **构建工具**: Gradle
- **并发**: Kotlin Coroutines + Flow

### 快速开始

#### 环境要求

- JDK 17+
- Kotlin 1.9+

#### 构建项目

```bash
./gradlew build
```

#### 运行

```bash
# 查看版本
./gradlew run --args="-v"

# 交互模式
./gradlew run --args="-i"

# 指定 prompt
./gradlew run --args="-p '你的问题'"
```

### 项目结构

```
src/main/kotlin/rj/cocacode/
├── Main.kt                  # CLI 入口
├── agents/                 # Agent 系统
├── bootstrap/              # 引导状态管理
├── bridge/                # 远程连接桥
├── buddy/                 # 伴侣系统
├── cache/                 # 缓存管理
├── cli/                   # CLI 工具
├── commands/               # 命令注册
├── concurrency/           # 并发工具
├── config/                 # 配置管理
├── constants/              # 常量定义
├── context/                # 上下文管理
├── debug/                  # 调试器
├── diff/                   # 差异引擎
├── engine/                 # 查询引擎
├── entrypoints/            # SDK 入口
├── ext/                    # 扩展管理
├── format/                 # 代码格式化
├── history/                # 历史记录
├── hooks/                  # 钩子系统
├── ink/                    # 文本处理
├── keybindings/            # 键盘绑定
├── mcp/                    # MCP 协议
├── memdir/                 # 记忆目录
├── metrics/                # 指标收集
├── model/                  # 模型选择
├── native-ts/              # 本地模块
├── network/                # 网络客户端
├── outputStyles/           # 输出样式
├── parser/                 # 代码解析
├── plugins/                # 插件系统
├── query/                  # 查询配置
├── realtime/               # 实时系统
├── remote/                 # 远程会话
├── repl/                   # REPL 循环
├── search/                 # 搜索引擎
├── security/                # 安全工具
├── services/               # 服务组件
├── skills/                 # 技能系统
├── state/                  # 状态管理
├── storage/                # 存储系统
├── stream/                 # 流处理
├── tasks/                  # 任务管理
├── telemetry/              # 遥测
├── template/               # 模板引擎
├── test/                   # 测试框架
├── tools/                  # 工具注册
├── types/                  # 类型定义
├── ui/                     # 终端UI
├── utils/                  # 工具函数
├── validate/               # 代码验证
├── version/                # 版本信息
├── vim/                    # Vim 支持
└── voice/                 # 语音模式
```

### 配置

配置文件通常位于 `~/.cocacode/config.json`:

```json
{
  "version": "1.0.0",
  "apiUrl": "https://api.anthropic.com",
  "logLevel": "INFO",
  "maxMemory": 1073741824,
  "enableTelemetry": true
}
```

### 许可证

本项目基于 SPDX 许可证授权。

---

## English

### Project Introduction

CocaCode is a powerful AI coding assistant designed for developers, integrating multiple AI models, tools, and extensibility features to help developers complete coding tasks more efficiently.

### Key Features

- **Multi-Agent System**: Support for custom AI Agents with flexible loading and switching
- **Rich Toolset**: Built-in file operations, search, code editing tools
  - `Bash` - Execute shell commands
  - `Read` - Read file contents
  - `Write` - Write file contents
  - `Edit` - Edit file contents
  - `Glob` - Find files by pattern
  - `Grep` - Search file contents
  - `WebSearch` - Search the web
- **REPL Mode**: Interactive programming environment
- **Extension System**: Plugin and extension loading support
- **Skills System**: Built-in skill loader and executor
- **Memory System**: Project memory and reference documentation
- **Context Management**: Intelligent context management for large codebases
- **Debugger**: Built-in debugging functionality
- **Diff Engine**: Powerful code diff comparison
- **LSP Support**: Language Server Protocol integration
- **MCP Client**: Model Context Protocol client support
- **Remote Sessions**: Remote collaboration and session management
- **Keybindings**: Customizable keyboard shortcuts
- **Vim Mode**: Full Vim editing support

### Tech Stack

- **Language**: Kotlin
- **Build Tool**: Gradle
- **Concurrency**: Kotlin Coroutines + Flow

### Quick Start

#### Requirements

- JDK 17+
- Kotlin 1.9+

#### Build

```bash
./gradlew build
```

#### Run

```bash
# Version
./gradlew run --args="-v"

# Interactive mode
./gradlew run --args="-i"

# With prompt
./gradlew run --args="-p 'your question'"
```

### Project Structure

```
src/main/kotlin/rj/cocacode/
├── Main.kt                  # CLI entry point
├── agents/                 # Agent system
├── bootstrap/              # Bootstrap state
├── bridge/                 # Remote bridge
├── buddy/                  # Companion system
├── cache/                  # Cache management
├── cli/                    # CLI utilities
├── commands/               # Command registry
├── concurrency/            # Concurrency utils
├── config/                 # Configuration
├── constants/              # Constants
├── context/                # Context management
├── debug/                  # Debugger
├── diff/                   # Diff engine
├── engine/                 # Query engine
├── entrypoints/            # SDK entry points
├── ext/                    # Extension manager
├── format/                  # Code formatting
├── history/                # History management
├── hooks/                  # Hook system
├── ink/                    # Text processing
├── keybindings/            # Keyboard bindings
├── mcp/                    # MCP protocol
├── memdir/                 # Memory directory
├── metrics/                # Metrics collection
├── model/                  # Model selector
├── native-ts/              # Native modules
├── network/                # Network client
├── outputStyles/          # Output styles
├── parser/                 # Code parser
├── plugins/                # Plugin system
├── query/                  # Query config
├── realtime/               # Real-time system
├── remote/                 # Remote sessions
├── repl/                   # REPL loop
├── search/                 # Search engine
├── security/               # Security utils
├── services/              # Service components
├── skills/                 # Skills system
├── state/                  # State management
├── storage/                # Storage system
├── stream/                 # Stream processing
├── tasks/                  # Task management
├── telemetry/              # Telemetry
├── template/               # Template engine
├── test/                   # Test framework
├── tools/                  # Tool registry
├── types/                  # Type definitions
├── ui/                     # Terminal UI
├── utils/                  # Utilities
├── validate/               # Code validation
├── version/                # Version info
├── vim/                    # Vim support
└── voice/                 # Voice mode
```

### Configuration

Config file typically at `~/.cocacode/config.json`:

```json
{
  "version": "1.0.0",
  "apiUrl": "https://api.anthropic.com",
  "logLevel": "INFO",
  "maxMemory": 1073741824,
  "enableTelemetry": true
}
```

### License

This project is licensed under SPDX license.