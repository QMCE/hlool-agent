# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew build    # Build the project (currently failing)
./gradlew run      # Run the application
./gradlew test     # Run tests (no tests exist yet)
```

Run with arguments: `./gradlew run --args="-i"` (interactive) or `--args="-p 'prompt'"`.

## Architecture

CocaCode is a Kotlin port of Claude Code (TypeScript reference at `../claude_code_src-master/src/`). It is an AI coding assistant CLI with a modular architecture of ~50 packages under `src/main/kotlin/rj/cocacode/`.

**Entry point**: `Main.kt` — Clikt CLI that either runs a single prompt or starts the REPL loop.

**Core loop**: `repl/ReplLoop.kt` — reads input, dispatches slash-commands (`/help`), shell escapes (`!command`), or sends plain text to `QueryEngine.processQuery()`.

**Settings system**: Multi-layer config merging modeled after Claude Code. Priority order (low→high): User → Project → Local → Flag → Policy. Key files:
- `config/SettingsConfig.kt` — loads/merges/saves settings JSON from disk
- `config/SettingsSource.kt` — enum defining the priority hierarchy
- `config/SettingsMerger.kt` — deep-merges JSON objects with array dedup
- `config/ApiConfig.kt` — API credentials (currently only reads from env vars)

**Hook system**: Event-driven hooks (`PreToolUse`, `PostToolUse`, `SessionStart`, `UserPromptSubmit`, etc.) registered in `HookRegistry`. Hooks can be shell commands, LLM prompts, HTTP calls, or agentic verifiers. Defined in `hooks/HookTypes.kt`.

**Tools**: Registered in `ToolRegistry` (`tools/ToolRegistry.kt`). Implementations: `BashTool`, `ReadFileTool`, `WriteFileTool`, `EditFileTool`, `GlobTool`, `GrepTool`.

**Permissions**: `permissions/Permissions.kt` and related files handle tool execution authorization.

**Types**: Core domain types live in `types/` — `Tool` and `ToolResult` are the critical ones that many files depend on.

## Build State

The build currently fails with ~270 compilation errors. Root causes:

1. **Import confusion**: `types/Tool.kt` defines `Tool` as a data class with `execute()` returning `ToolResult`, but many files try to import `Tool` from `engine/` or other non-existent packages. Similarly, `ToolResult` in `types/ToolResult.kt` uses fields `isError`/`content` but callers reference `.text` or construct it with different parameter names.

2. **Redeclaration conflicts**: `ThinkingConfig` declared in both `utils/Thinking.kt` and `utils/ThinkingConfig.kt`. `TokenEstimator` in both `utils/TokenEstimator.kt` and `utils/ThinkingConfig.kt`. `QuerySource` enum in both `query/Query.kt` and `query/StopHooks.kt`. Several internal types redeclared across `query/Query.kt` and `query/Deps.kt`.

3. **Incomplete query engine**: `query/Query.kt` (1371 lines) and `query/Deps.kt` are the most broken — ported from TypeScript with many missing types, incorrect parameter names, and type mismatches. The query/ directory is the main blocker.

4. **Missing methods**: `ApiClient` is an empty class with no `post()` method. `ApiConfig` properties are instance fields but accessed as companion object members. `AppStateManager` has no `addMessage()` or `setThinking()` methods.

## Key Conventions

- Package prefix: `rj.cocacode`
- Config dir: `~/.cocacode/` (env override: `COCACODE_CONFIG_DIR`, fallback: `CLAUDE_CONFIG_DIR`)
- Settings files: `settings.json` (user), `.cocacode/settings.json` (project), `.cocacode/settings.local.json` (local)
- Env vars: `COCACODE_` prefix, falls back to `COCA_`
