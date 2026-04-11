# CocaCode

CocaCode - AI Coding Assistant | AI 编程助手

## Project Introduction

CocaCode is a powerful AI coding assistant designed for developers, integrating multiple AI models, tools, and extensibility features to help developers complete coding tasks more efficiently.

## Key Features

- **Multi-Agent System**: Support for custom AI Agents with flexible loading and switching
- **Toolset**: Built-in file operations, search, code editing tools
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

## Tech Stack

- **Language**: Kotlin
- **Build Tool**: Gradle
- **Concurrency**: Kotlin Coroutines + Flow
- **HTTP Client**: Ktor
- **Terminal**: JLine
- **Logging**: SLF4J + Logback
- **CLI**: Clikt
- **Serialization**: Kotlinx Serialization

## Quick Start

### Requirements

- JDK 25+
- Kotlin 2.3+

### Build

```bash
./gradlew build
```

### Run

```bash
# Version
./gradlew run --args="-v"

# Interactive mode
./gradlew run --args="-i"

# With prompt
./gradlew run --args="-p 'your question'"
```

## Project Structure

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
├── voice/                 # Voice mode
└── workspace/              # Workspace
```

## Configuration

Config file typically at `~/.cocacode/config.json`:

```json
{
  "apiUrl": "https://api.anthropic.com",
  "logLevel": "INFO",
  "maxMemory": 1073741824,
  "enableTelemetry": true
}
```

## License

This project is licensed under BSD 3-Clause License, see [LICENSE](LICENSE) file for details.