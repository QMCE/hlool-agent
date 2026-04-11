# CocaCode

CocaCode - AI Coding Assistant | AI 程式設計助手

## 專案簡介

CocaCode 是一個強大的 AI 程式設計助手，专为开发者設計，集成了多種 AI 模型、工具和擴展能力，幫助开发者更高效地完成編碼任務。

## 主要特性

- **多 Agent 系統**：支援自定義 AI Agent，可載入多個 Agent 定義，靈活切換
- **工具集**：內建檔案操作、搜尋、程式碼編輯等工具
  - `Bash` - 執行 Shell 命令
  - `Read` - 讀取檔案內容
  - `Write` - 寫入檔案內容
  - `Edit` - 編輯檔案內容
  - `Glob` - 按模式搜尋檔案
  - `Grep` - 搜尋檔案內容
  - `WebSearch` - 搜尋網路
- **REPL 互動模式**：支援互動式編程環境
- **擴展系統**：支援外掛程式和擴展載入
- **技能系統**：內建技能載入器和執行器
- **記憶系統**：自動儲存專案記憶和參考文件
- **上下文管理**：智慧上下文管理，支援大規模程式碼庫
- **除錯器**：內建除錯功能
- **差異引擎**：強大的程式碼 diff 比較能力
- **LSP 支援**：Language Server Protocol 整合
- **MCP 客戶端**：Model Context Protocol 客戶端支援
- **遠端會話**：支援遠端協作和會話管理
- **鍵盤綁定**：可自定義鍵盤快捷鍵
- **Vim 模式**：完整的 Vim 編輯支援

## 技術棧

- **語言**：Kotlin
- **建置工具**：Gradle
- **並發**：Kotlin Coroutines + Flow
- **HTTP 客戶端**：Ktor
- **終端**：JLine
- **日誌**：SLF4J + Logback
- **CLI**：Clikt
- **序列化**：Kotlinx Serialization

## 快速開始

### 環境要求

- JDK 25+
- Kotlin 2.3+

### 建置專案

```bash
./gradlew build
```

### 執行

```bash
# 版本
./gradlew run --args="-v"

# 互動模式
./gradlew run --args="-i"

# 指定 prompt
./gradlew run --args="-p '你的問題'"
```

## 專案結構

```
src/main/kotlin/rj/cocacode/
├── Main.kt                  # CLI 入口
├── agents/                 # Agent 系統
├── bootstrap/              # 引導狀態管理
├── bridge/                # 遠端連接橋
├── buddy/                 # 伴侶系統
├── cache/                 # 快取管理
├── cli/                   # CLI 工具
├── commands/               # 命令註冊
├── concurrency/           # 並發工具
├── config/                 # 配置管理
├── constants/              # 常數定義
├── context/                # 上下文管理
├── debug/                  # 除錯器
├── diff/                   # 差異引擎
├── engine/                 # 查詢引擎
├── entrypoints/            # SDK 入口
├── ext/                    # 擴展管理
├── format/                 # 程式碼格式化
├── history/                # 歷史記錄
├── hooks/                  # 鉤子系統
├── ink/                    # 文字處理
├── keybindings/            # 鍵盤綁定
├── mcp/                    # MCP 協定
├── memdir/                 # 記憶目錄
├── metrics/                # 指標收集
├── model/                  # 模型選擇
├── native-ts/              # 本地模組
├── network/                # 網路客戶端
├── outputStyles/           # 輸出樣式
├── parser/                 # 程式碼解析
├── plugins/                # 外掛程式系統
├── query/                  # 查詢配置
├── realtime/               # 即時系統
├── remote/                 # 遠端會話
├─�� repl/                   # REPL 迴圈
├── search/                 # 搜尋引擎
├── security/                # 安全工具
├── services/               # 服務元件
├── skills/                 # 技能系統
├── state/                  # 狀態管理
├── storage/                # 儲存系統
├── stream/                 # 流處理
├── tasks/                  # 任務管理
├── telemetry/              # 遙測
├── template/               # 模板引擎
├── test/                   # 測試框架
├── tools/                  # 工具註冊
├── types/                  # 類型定義
├── ui/                     # 終端 UI
├── utils/                  # 工具函式
├── validate/               # 程式碼驗證
├── version/                # 版本資訊
├── vim/                    # Vim 支援
├── voice/                 # 語音模式
└── workspace/              # 工作空間
```

## 配置

設定檔通常位於 `~/.cocacode/config.json`：

```json
{
  "apiUrl": "https://api.anthropic.com",
  "logLevel": "INFO",
  "maxMemory": 1073741824,
  "enableTelemetry": true
}
```

## 授權條款

本專案基於 BSD 3-Clause 授權條款，詳見 [LICENSE](LICENSE) 檔案。