# Hlool Agent

Hlool Agent — AI Coding Assistant for [SHUAI API](https://api.shuaiapi.com) | AI 编程助手

[English](README.en.md) | [正體中文](README.hant.md)

### 一键登录（Access Token）

**不接受手动粘贴 sk-**。凭证是控制台 **Access Token**；登录后自动申领并持久化名为 `Hlool Agent` 的 relay sk-。

```text
/login                      # 账号密码 → 持久化 access token + 自动申领 sk-
/login access <token>       # 粘贴个人设置里的 Access Token
/login oauth                # 浏览器登录后粘贴 Access Token（不是 sk-）
/login refresh              # 重新申领 relay sk-
/console                    # 网页管理能力（余额/令牌/模型/日志/充值…）
```

配置：`~/.hlool-agent/config.json`（`accessToken` + 自动写入的 `apiKey`）。

环境变量：`HLOOL_ACCESS_TOKEN` / `HLOOL_USER_ID` / `HLOOL_BASE_URL`（亦兼容 `NEWAPI_*`）。

### 控制台命令

| 命令 | 说明 |
|------|------|
| `/console balance` | 账户余额与配额 |
| `/console models` | 可用模型 |
| `/console groups` | 分组 |
| `/console tokens` | 令牌列表（密钥脱敏） |
| `/console create-token <name>` | 创建令牌 |
| `/console switch-group <id> <g>` | 切换分组 |
| `/console enable-token` / `disable-token` / `delete-token` | 启停删 |
| `/console logs` | 调用日志 |
| `/console topup` / `topup-history` | 充值信息 |
| `/console notice` / `pricing` / `aff` | 公告 / 定价 / 邀请码 |
| `/console claim` | 重新申领 Agent relay sk- |

### 构建

```bash
./gradlew jvmFatJar
java -jar build/libs/hlool-agent-*.jar -i
```

### 许可证

BSD 3-Clause，详见 [LICENSE](LICENSE)。
