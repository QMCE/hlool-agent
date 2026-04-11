# CocaCode

CocaCode - AI Coding Assistant | AI 编程助手

[English](README.en.md) | [正體中文](README.hant.md)

### 项目简介

CocaCode 是一个强大的 AI 编程助手，专为开发者设计，集成了多种 AI 模型、工具和扩展能力，帮助开发者更高效地完成编码任务。

### 主要特性

- **多 Agent 系统**：支持自定义 AI Agent，可加载多个 Agent 定义，灵活切换
- **工具集**：内置文件操作、搜索、代码编辑等工具
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
- **差异引擎**：强大的代码 diff 对比能力
- **LSP 支持**：Language Server Protocol 集成
- **MCP 客户端**：Model Context Protocol 客户端支持
- **远程会话**：支持远程协作和会话管理
- **键盘绑定**：可自定义键盘快捷键
- **Vim 模式**：完整的 Vim 编辑支持

### 技术栈

- **语言**：Kotlin
- **构建工具**：Gradle
- **并发**：Kotlin Coroutines + Flow
- **HTTP 客户端**：Ktor
- **终端**：JLine
- **日志**：SLF4J + Logback
- **CLI**：Clikt
- **序列化**：Kotlinx Serialization

### 快速开始

#### 环境要求

- JDK 25+
- Kotlin 2.3+

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
├── ui/                     # 终端 UI
├── utils/                  # 工具函数
├── validate/               # 代码验证
├── version/                # 版本信息
├── vim/                    # Vim 支持
├── voice/                 # 语音模式
└── workspace/              # 工作空间
```

### 配置

配置文件通常位于 `~/.cocacode/config.json`：

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

本项目基于 BSD 3-Clause 许可证授权，详见 [LICENSE](LICENSE) 文件。
