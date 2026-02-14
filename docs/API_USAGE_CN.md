# MultiCurrencyEconomy API 使用文档（中文）

## 1. 文档说明

本文档描述的是 **插件内 SDK API（Kotlin/Java 方法调用）**，不是 HTTP 接口。

- 接口入口：`MultiCurrencyEconomyApi`
- 操作器：`EconomyOperator`
- 主要返回对象：`EconomyResult`、`CurrencyInfo`、`AccountSnapshot`

## 2. 错误码说明

插件原生返回 `EconomyResult(success, balance, message)`，没有独立 `code` 字段。
为便于上层系统统一处理，建议按 `message` 映射如下错误码：

| 错误码 | 含义 | 典型触发场景 |
|---|---|---|
| `MCE-API-000` | 成功 | `success=true` |
| `MCE-API-001` | API 未就绪 | `MultiCurrencyEconomyApi.delegate()` 抛出 `IllegalStateException` |
| `MCE-BIZ-001` | 货币不存在 | `message` 包含“找不到货币” |
| `MCE-BIZ-002` | 货币已禁用 | `message` 包含“货币已禁用” |
| `MCE-BIZ-003` | 金额非法 | `message` 包含“金额必须为正数/非负” |
| `MCE-BIZ-004` | 余额不足 | `message` 包含“余额不足” |
| `MCE-BIZ-005` | 超过上限 | `message` 包含“超过余额上限” |
| `MCE-BIZ-006` | 并发冲突 | `message` 包含“并发冲突” |
| `MCE-BIZ-999` | 其他业务失败 | 其他 `success=false` 场景 |

---

## 3. API 接口详情

## 3.1 MultiCurrencyEconomyApi.isReady

- 用途：检测 API 是否已可用。
- 请求方法：SDK 同步方法调用。
- 方法签名：`fun isReady(): Boolean`

### 参数说明

无参数。

### 返回示例

```json
true
```

### 错误说明

- 无异常抛出。
- `false` 表示插件未完成初始化。

### 可直接使用的中文 Prompt

```text
请帮我写一段 Kotlin 代码：先调用 MultiCurrencyEconomyApi.isReady()，如果未就绪就打印“经济系统未就绪”，否则打印“经济系统已就绪”。
```

---

## 3.2 MultiCurrencyEconomyApi.delegate

- 用途：创建绑定操作人身份的经济操作器。
- 请求方法：SDK 同步方法调用。
- 方法签名：`fun delegate(operatorId: String): EconomyOperator`

### 参数说明

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `operatorId` | `String` | 是 | 操作人标识（建议使用插件名或系统名） |

### 返回示例

```json
{
  "operatorId": "MyPlugin"
}
```

### 错误说明

- `MCE-API-001`：API 未初始化，抛 `IllegalStateException`。

### 可直接使用的中文 Prompt

```text
请生成 Kotlin 示例：使用 operatorId=MyPlugin 调用 MultiCurrencyEconomyApi.delegate()，并在拿到 EconomyOperator 后打印操作人标识。
```

---

## 3.3 MultiCurrencyEconomyApi.getBalance

- 用途：查询某玩家在指定货币下的余额。
- 请求方法：SDK 同步方法调用。
- 方法签名：`fun getBalance(playerName: String, currencyIdentifier: String): BigDecimal`

### 参数说明

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `playerName` | `String` | 是 | 玩家名称 |
| `currencyIdentifier` | `String` | 是 | 货币标识（如 `coin`） |

### 返回示例

```json
"1234.50"
```

### 错误说明

- `MCE-API-001`：API 未就绪，抛异常。

### 可直接使用的中文 Prompt

```text
请写 Kotlin 代码：查询玩家 Notch 在 coin 货币下的余额，并以“玩家余额：xxx”格式输出。
```

---

## 3.4 MultiCurrencyEconomyApi.getActiveCurrencies

- 用途：获取所有已启用货币。
- 请求方法：SDK 同步方法调用。
- 方法签名：`fun getActiveCurrencies(): List<CurrencyInfo>`

### 参数说明

无参数。

### 返回示例

```json
[
  {
    "id": 1,
    "identifier": "coin",
    "displayName": "金币",
    "symbol": "￥",
    "precision": 2,
    "defaultMaxBalance": -1,
    "primary": true,
    "enabled": true
  }
]
```

### 错误说明

- `MCE-API-001`：API 未就绪，抛异常。

### 可直接使用的中文 Prompt

```text
请生成 Kotlin 代码：调用 MultiCurrencyEconomyApi.getActiveCurrencies()，遍历打印每个货币的标识、显示名、精度和是否主货币。
```

---

## 3.5 MultiCurrencyEconomyApi.getPrimaryCurrency

- 用途：获取当前主货币。
- 请求方法：SDK 同步方法调用。
- 方法签名：`fun getPrimaryCurrency(): CurrencyInfo?`

### 参数说明

无参数。

### 返回示例

```json
{
  "id": 1,
  "identifier": "coin",
  "displayName": "金币",
  "symbol": "￥",
  "precision": 2,
  "defaultMaxBalance": -1,
  "primary": true,
  "enabled": true
}
```

或

```json
null
```

### 错误说明

- `MCE-API-001`：API 未就绪，抛异常。

### 可直接使用的中文 Prompt

```text
请写 Kotlin 示例：获取主货币，如果为空输出“未配置主货币”，否则输出主货币 identifier 与 displayName。
```

---

## 3.6 MultiCurrencyEconomyApi.getPlayerAccounts

- 用途：获取玩家在所有启用货币下的账户快照。
- 请求方法：SDK 同步方法调用。
- 方法签名：`fun getPlayerAccounts(playerName: String): List<AccountSnapshot>`

### 参数说明

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `playerName` | `String` | 是 | 玩家名称 |

### 返回示例

```json
[
  {
    "playerName": "Notch",
    "playerUuid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "currencyIdentifier": "coin",
    "currencyDisplayName": "金币",
    "currencySymbol": "￥",
    "currencyPrecision": 2,
    "balance": "100.00",
    "formattedBalance": "￥100.00"
  }
]
```

### 错误说明

- `MCE-API-001`：API 未就绪，抛异常。

### 可直接使用的中文 Prompt

```text
请帮我写 Kotlin 代码：查询玩家 Notch 的所有账户快照，并逐行打印“货币标识 - 格式化余额”。
```

---

## 3.7 EconomyOperator.add

- 用途：增加余额（存款）。
- 请求方法：SDK 同步方法调用（内部异步落库）。
- 方法签名：`fun add(playerName: String, currencyIdentifier: String, amount: BigDecimal, reason: String): EconomyResult`

### 参数说明

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `playerName` | `String` | 是 | 玩家名称 |
| `currencyIdentifier` | `String` | 是 | 货币标识 |
| `amount` | `BigDecimal` | 是 | 增加金额，必须大于 0 |
| `reason` | `String` | 是 | 操作原因（审计必填） |

### 返回示例

```json
{
  "success": true,
  "balance": "210.00",
  "message": ""
}
```

### 错误说明

- `MCE-BIZ-001`：货币不存在
- `MCE-BIZ-002`：货币禁用
- `MCE-BIZ-003`：金额非法
- `MCE-BIZ-005`：超过上限
- `MCE-BIZ-006`：并发冲突

### 可直接使用的中文 Prompt

```text
请生成 Kotlin 代码：通过 MultiCurrencyEconomyApi.delegate("活动系统") 获取操作器，为玩家 Notch 在 coin 货币增加 100.00，原因写“活动奖励”，并打印 success、balance、message。
```

---

## 3.8 EconomyOperator.take

- 用途：扣减余额（取款）。
- 请求方法：SDK 同步方法调用（内部异步落库）。
- 方法签名：`fun take(playerName: String, currencyIdentifier: String, amount: BigDecimal, reason: String): EconomyResult`

### 参数说明

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `playerName` | `String` | 是 | 玩家名称 |
| `currencyIdentifier` | `String` | 是 | 货币标识 |
| `amount` | `BigDecimal` | 是 | 扣减金额，必须大于 0 |
| `reason` | `String` | 是 | 操作原因（审计必填） |

### 返回示例

```json
{
  "success": false,
  "balance": "15.00",
  "message": "余额不足"
}
```

### 错误说明

- `MCE-BIZ-001`：货币不存在
- `MCE-BIZ-002`：货币禁用
- `MCE-BIZ-003`：金额非法
- `MCE-BIZ-004`：余额不足
- `MCE-BIZ-006`：并发冲突

### 可直接使用的中文 Prompt

```text
请帮我写 Kotlin 示例：调用 EconomyOperator.take 从玩家 Notch 的 coin 账户扣除 30.00，原因“商城消费”，并按成功/失败分别输出中文提示。
```

---

## 3.9 EconomyOperator.set

- 用途：直接设置目标余额。
- 请求方法：SDK 同步方法调用（内部异步落库）。
- 方法签名：`fun set(playerName: String, currencyIdentifier: String, amount: BigDecimal, reason: String): EconomyResult`

### 参数说明

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `playerName` | `String` | 是 | 玩家名称 |
| `currencyIdentifier` | `String` | 是 | 货币标识 |
| `amount` | `BigDecimal` | 是 | 目标余额，必须大于等于 0 |
| `reason` | `String` | 是 | 操作原因（审计必填） |

### 返回示例

```json
{
  "success": true,
  "balance": "500.00",
  "message": ""
}
```

### 错误说明

- `MCE-BIZ-001`：货币不存在
- `MCE-BIZ-002`：货币禁用
- `MCE-BIZ-003`：金额非法
- `MCE-BIZ-006`：并发冲突

### 可直接使用的中文 Prompt

```text
请写 Kotlin 代码：将玩家 Notch 在 point 货币下余额设置为 500，原因“管理员校正”，并输出操作结果详情。
```

---

## 3.10 EconomyOperator.getBalance

- 用途：通过已绑定操作器查询余额（等同静态查询，但便于统一调用风格）。
- 请求方法：SDK 同步方法调用。
- 方法签名：`fun getBalance(playerName: String, currencyIdentifier: String): BigDecimal`

### 参数说明

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `playerName` | `String` | 是 | 玩家名称 |
| `currencyIdentifier` | `String` | 是 | 货币标识 |

### 返回示例

```json
"320.00"
```

### 错误说明

- 当 API 未就绪时，通常在创建 `EconomyOperator` 阶段即已抛错。

### 可直接使用的中文 Prompt

```text
请生成 Kotlin 示例：先创建 EconomyOperator，再查询玩家 Notch 在 coin 的余额，并格式化输出“当前余额：xxx”。
```

---

## 4. 事件接口（可选监听）

## 4.1 BalanceChangeEvent（变更前，可取消）

- 用途：余额变更前拦截。
- 触发阶段：`add/take/set` 在真正生效前。
- 可取消：是。

### 可直接使用的中文 Prompt

```text
请写一个 Bukkit 监听器示例：监听 BalanceChangeEvent，当 type=WITHDRAW 且 amount>1000 时取消事件，并给出中文注释。
```

## 4.2 BalanceChangePostEvent（变更后，不可取消）

- 用途：余额变更后通知。
- 触发阶段：余额已更新后。
- 可取消：否。

### 可直接使用的中文 Prompt

```text
请生成 Bukkit 监听器代码：监听 BalanceChangePostEvent，把玩家名、货币标识、变更后余额记录到控制台，注释使用中文。
```

---

## 5. 调用建议

- 业务写操作统一通过 `EconomyOperator`，并填写清晰 `reason`。
- 调用前先检查 `MultiCurrencyEconomyApi.isReady()`。
- 对 `EconomyResult` 做统一错误码映射，便于前端/日志/告警系统处理。
- 高并发场景上线前执行 `/eco-test-concurrency` 与 `/eco-test-stability`。
