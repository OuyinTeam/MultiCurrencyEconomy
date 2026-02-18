# MultiCurrencyEconomy API 使用文档（中文）

## 1. 文档说明

本文档描述的是 **插件内 SDK API（Kotlin/Java 方法调用）**，不是 HTTP 接口。

- 接口入口：`MultiCurrencyEconomyApi`
- 操作器：`EconomyOperator`
- 主要返回对象：`EconomyResult`、`CurrencyInfo`、`AccountSnapshot`

---

## 2. 快速开始：如何在您的插件中集成

### 2.1 添加 Maven 仓库

在您的项目 `build.gradle.kts` 中添加仓库：

```kotlin
repositories {
    maven("https://maven.wcpe.top/repository/maven-public/")
    mavenCentral()
}
```

### 2.2 添加 API 依赖

在 `dependencies` 块中添加 **compileOnly** 依赖：

```kotlin
dependencies {
    // 编译时依赖，运行时由服务器提供
    compileOnly("top.wcpe.mc.plugin:multicurrencyeconomy-api:VERSION")
    
    // 如果您使用 TabooLib
    compileOnly(kotlin("stdlib"))
}
```

> **重要说明**：
> - 使用 `compileOnly`，不要用 `implementation`，避免将 API 打包到您的插件中
> - 将 `VERSION` 替换为实际版本号（如 `1.0.0-SNAPSHOT`）
> - 确保您的服务器已安装 MultiCurrencyEconomy 插件

### 2.3 配置插件依赖（plugin.yml）

在您的 `plugin.yml` 中声明插件依赖：

```yaml
name: YourPlugin
version: 1.0.0
main: your.package.YourPlugin
depend: [MultiCurrencyEconomy]  # 硬依赖（必须安装）
# 或使用软依赖：
# softdepend: [MultiCurrencyEconomy]
```

### 2.4 基本使用示例

> ✅ **重要提示：MCE 使用主线程同步初始化**
> 
> 可以直接在 `onEnable()` 中同步调用 `isReady()` 和 `delegate()`。
> 只需确保在 `plugin.yml` 中配置 `depend: [MultiCurrencyEconomy]`。

```kotlin
import org.bukkit.plugin.java.JavaPlugin
import top.wcpe.mc.plugin.multicurrencyeconomy.api.MultiCurrencyEconomyApi
import top.wcpe.mc.plugin.multicurrencyeconomy.api.EconomyOperator
import java.math.BigDecimal

class YourPlugin : JavaPlugin() {
    private var economyOperator: EconomyOperator? = null
    
    override fun onEnable() {
        // 直接同步初始化
        initializeMCE()
    }
    
    private fun initializeMCE() {
        // 检查 API 是否就绪
        if (!MultiCurrencyEconomyApi.isReady()) {
            logger.warning("MultiCurrencyEconomy API 未就绪")
            logger.warning("请确保：1) MCE 已安装 2) plugin.yml 中配置 depend: [MultiCurrencyEconomy]")
            return
        }
        
        // 创建操作器（绑定操作者标识）
        economyOperator = MultiCurrencyEconomyApi.delegate("YourPlugin")
        logger.info("MultiCurrencyEconomy 集成成功")
    }
    
    // 示例：查询余额
    fun getPlayerBalance(playerName: String, currencyId: String): BigDecimal {
        return economyOperator?.getBalance(playerName, currencyId) ?: BigDecimal.ZERO
    }
    
    // 示例：扣除货币
    fun takePlayerMoney(playerName: String, currencyId: String, amount: BigDecimal): Boolean {
        val result = economyOperator?.take(playerName, currencyId, amount, "商品购买")
        return result?.success ?: false
    }
}
```

### 2.5 完整集成示例（Hook 类）

推荐创建一个独立的 Hook 类来管理 MultiCurrencyEconomy 集成：

```kotlin
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import top.wcpe.mc.plugin.multicurrencyeconomy.api.MultiCurrencyEconomyApi
import top.wcpe.mc.plugin.multicurrencyeconomy.api.EconomyOperator
import java.math.BigDecimal

object MultiCurrencyEconomyHook {
    private var available = false
    private var operator: EconomyOperator? = null
    private const val OPERATOR_ID = "YourPlugin"

    /** 初始化（在插件 onEnable 中调用） */
    fun init() {
        // 检查插件是否存在
        if (Bukkit.getPluginManager().getPlugin("MultiCurrencyEconomy") == null) {
            println("[MCE] 未检测到 MultiCurrencyEconomy 插件")
            return
        }
        
        try {
            // 检查 API 是否就绪
            if (!MultiCurrencyEconomyApi.isReady()) {
                println("[MCE] MultiCurrencyEconomy API 未就绪")
                return
            }
            
            // 创建操作器
            operator = MultiCurrencyEconomyApi.delegate(OPERATOR_ID)
            available = true
            println("[MCE] MultiCurrencyEconomy 集成已启用")
        } catch (e: Exception) {
            println("[MCE] MultiCurrencyEconomy 初始化失败: ${e.message}")
            available = false
        }
    }

    /** 是否可用 */
    fun isAvailable(): Boolean = available

    /** 查询余额 */
    fun getBalance(player: Player, currencyId: String): BigDecimal {
        if (!available) return BigDecimal.ZERO
        return runCatching {
            operator!!.getBalance(player.name, currencyId)
        }.getOrDefault(BigDecimal.ZERO)
    }

    /** 检查余额是否足够 */
    fun hasBalance(player: Player, currencyId: String, amount: BigDecimal): Boolean {
        return getBalance(player, currencyId) >= amount
    }

    /** 扣除货币 */
    fun take(player: Player, currencyId: String, amount: BigDecimal, reason: String): Boolean {
        if (!available) return false
        return runCatching {
            val result = operator!!.take(player.name, currencyId, amount, reason)
            result.success
        }.getOrDefault(false)
    }
    
    /** 增加货币 */
    fun add(player: Player, currencyId: String, amount: BigDecimal, reason: String): Boolean {
        if (!available) return false
        return runCatching {
            val result = operator!!.add(player.name, currencyId, amount, reason)
            result.success
        }.getOrDefault(false)
    }
}
```

**使用方式（推荐 ✅）：**

```kotlin
class YourPlugin : JavaPlugin() {
    override fun onEnable() {
        // 直接同步初始化
        MultiCurrencyEconomyHook.init()
        
        if (MultiCurrencyEconomyHook.isAvailable()) {
            logger.info("✓ MultiCurrencyEconomy 集成成功")
        } else {
            logger.warning("✗ MultiCurrencyEconomy 集成失败")
            logger.warning("请检查：plugin.yml 是否配置 depend: [MultiCurrencyEconomy]")
        }
    }
}
```

**使用方式（最简单）：**

直接使用 [MCEHook_Example.kt](MCEHook_Example.kt) 示例代码，一行搞定：

```kotlin
class YourPlugin : JavaPlugin() {
    override fun onEnable() {
        MCEHook.initialize(this)  // 自动初始化，含错误处理
    }
}
```

**确保在 plugin.yml 中配置依赖：**

```yaml
name: YourPlugin
depend: [MultiCurrencyEconomy]  # 必须配置！
```

**业务代码使用示例：**

```kotlin
// 在业务代码中使用
fun buyItem(player: Player, price: BigDecimal) {
    if (!MultiCurrencyEconomyHook.isAvailable()) {
        player.sendMessage("§c经济系统不可用")
        return
    }
    
    if (!MultiCurrencyEconomyHook.hasBalance(player, "coin", price)) {
        player.sendMessage("§c余额不足")
        return
    }
    
    if (MultiCurrencyEconomyHook.take(player, "coin", price, "购买商品")) {
        player.sendMessage("§a购买成功")
    } else {
        player.sendMessage("§c扣款失败")
    }
}
```

### 2.6 API 使用方式对比

MultiCurrencyEconomy 提供了两种 API 使用方式：

| 对比项 | 静态方法 | EconomyOperator 方法 |
|---|---|---|
| **调用方式** | `MultiCurrencyEconomyApi.getBalance()` | `operator.getBalance()` |
| **前置条件** | 无需创建操作器 | 需先调用 `delegate()` 创建 |
| **适用场景** | 简单查询操作 | 需要记录操作者的业务场景 |
| **操作者标识** | 无 | 自动关联 operatorId |
| **支持的操作** | 查询方法（getBalance、getCurrencies 等） | 查询 + 写操作（add、take、set） |
| **审计记录** | 不记录操作者（查询不需要） | 所有写操作记录 operatorId |

**推荐实践：**
- ✅ **推荐**：使用 `EconomyOperator`（通过 `delegate()` 创建），便于统一管理和审计
- ⚠️ **可选**：直接使用静态方法快速查询（仅用于纯查询场景）

**代码示例：**

```kotlin
// 方式一：静态方法（仅查询）
val balance = MultiCurrencyEconomyApi.getBalance("PlayerName", "coin")

// 方式二：EconomyOperator（推荐，支持所有操作）
val operator = MultiCurrencyEconomyApi.delegate("MyPlugin")
val balance = operator.getBalance("PlayerName", "coin")
val result = operator.take("PlayerName", "coin", BigDecimal("10"), "购买商品")
```

---

## 3. 错误码说明

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

## 4. API 接口详情

## 4.1 MultiCurrencyEconomyApi.isReady

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

## 4.2 MultiCurrencyEconomyApi.delegate

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

## 4.3 MultiCurrencyEconomyApi.getBalance

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

## 4.4 MultiCurrencyEconomyApi.getActiveCurrencies

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

## 4.5 MultiCurrencyEconomyApi.getPrimaryCurrency

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

## 4.6 MultiCurrencyEconomyApi.getPlayerAccounts

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

## 4.7 EconomyOperator.add

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

## 4.8 EconomyOperator.take

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

## 4.9 EconomyOperator.set

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

## 4.10 EconomyOperator.getBalance

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

## 5. 事件接口（可选监听）

## 5.1 BalanceChangeEvent（变更前，可取消）

- 用途：余额变更前拦截。
- 触发阶段：`add/take/set` 在真正生效前。
- 可取消：是。

### 可直接使用的中文 Prompt

```text
请写一个 Bukkit 监听器示例：监听 BalanceChangeEvent，当 type=WITHDRAW 且 amount>1000 时取消事件，并给出中文注释。
```

## 5.2 BalanceChangePostEvent（变更后，不可取消）

- 用途：余额变更后通知。
- 触发阶段：余额已更新后。
- 可取消：否。

### 可直接使用的中文 Prompt

```text
请生成 Bukkit 监听器代码：监听 BalanceChangePostEvent，把玩家名、货币标识、变更后余额记录到控制台，注释使用中文。
```

---

## 6. 调用建议

- 业务写操作统一通过 `EconomyOperator`，并填写清晰 `reason`。
- 调用前先检查 `MultiCurrencyEconomyApi.isReady()`。
- 对 `EconomyResult` 做统一错误码映射，便于前端/日志/告警系统处理。
- 高并发场景上线前执行 `/eco-test-concurrency` 与 `/eco-test-stability`。
---

## 7. 故障排查指南

### 7.1 问题：无法加载到 API / ClassNotFoundException

**可能原因 1：未添加或错误添加依赖**

检查您的 `build.gradle.kts` 是否正确配置：

```kotlin
repositories {
    maven("https://maven.wcpe.top/repository/maven-public/")  // ✅ 必须添加仓库
}

dependencies {
    compileOnly("top.wcpe.mc.plugin:multicurrencyeconomy-api:VERSION")  // ✅ 使用 compileOnly
    // ❌ 错误：implementation("top.wcpe.mc.plugin:multicurrencyeconomy-api:VERSION")
}
```

**可能原因 2：服务器未安装 MultiCurrencyEconomy 插件**

- 确保服务器 `plugins` 目录下有 `MultiCurrencyEconomy.jar`
- 启动服务器查看控制台，确认插件已加载
- 执行 `/plugins` 命令，确认 MultiCurrencyEconomy 显示为绿色

**可能原因 3：未配置插件依赖**

检查您的 `plugin.yml`：

```yaml
depend: [MultiCurrencyEconomy]  # 硬依赖
# 或
softdepend: [MultiCurrencyEconomy]  # 软依赖
```

**可能原因 4：版本不匹配**

- 确保 API 版本与服务器上的插件版本一致
- 查看 MultiCurrencyEconomy 插件版本：`/version MultiCurrencyEconomy`
- 在 `build.gradle.kts` 中使用相同版本

---

### 7.2 问题：API 未就绪 / IllegalStateException

**症状：**
```
java.lang.IllegalStateException: MultiCurrencyEconomy API 尚未初始化
```

**根本原因：**

您的插件在 MCE 之前加载，导致 MCE 还未初始化。

MCE 在 `onEnable()` 中**主线程同步初始化**，如果您的插件先于 MCE 加载，此时 API 还未初始化。

---

**解决方案（必须 ✅）：配置插件加载顺序**

在 `plugin.yml` 中配置硬依赖，确保 MCE 先于您的插件加载：

```yaml
name: YourPlugin
depend: [MultiCurrencyEconomy]  # 硬依赖，MCE 必须先加载
```

或使用软依赖：

```yaml
softdepend: [MultiCurrencyEconomy]  # 软依赖，MCE 不存在时不报错
```

**配置后即可直接同步调用：**

```kotlin
class YourPlugin : JavaPlugin() {
    override fun onEnable() {
        // MCE 已先初始化，可以直接同步调用
        if (!MultiCurrencyEconomyApi.isReady()) {
            logger.warning("MCE API 未就绪，请检查：")
            logger.warning("1) MCE 插件是否已安装")
            logger.warning("2) CoreLib 是否已安装")
            logger.warning("3) 数据库连接是否正常")
            return
        }
        
        val operator = MultiCurrencyEconomyApi.delegate("MyPlugin")
        logger.info("✓ MCE API 初始化成功")
    }
}
```

---

**其他可能原因：**

1. **MCE 插件未安装**
   - 检查 `plugins` 目录下是否有 `MultiCurrencyEconomy.jar`
   - 执行 `/plugins` 确认 MCE 显示为绿色

2. **CoreLib 未安装**
   - MCE 依赖 CoreLib，必须先安装
   - 执行 `/plugins` 检查 CoreLib 是否为绿色（已加载）

3. **数据库初始化失败**
   - 查看服务器启动日志
   - 检查 MCE 配置文件中的数据库配置是否正确
   - 确认数据库连接正常

---

**推荐实践：**

| 步骤 | 操作 |
|------|------|
| 1 | 在 `plugin.yml` 中配置 `depend: [MultiCurrencyEconomy]` |
| 2 | 确保 MCE 和 CoreLib 已安装 |
| 3 | 直接在 `onEnable` 中同步调用 `isReady()` |
| 4 | 检查返回值，处理未就绪情况 |

**最简单的方式：直接使用 [MCEHook_Example.kt](MCEHook_Example.kt) 示例代码！**

---

### 7.3 问题：编译错误 / 找不到类

**症状：**
```
Unresolved reference: MultiCurrencyEconomyApi
```

**解决方案 1：刷新 Gradle 依赖**

在 IDE 中：
- **IntelliJ IDEA**：右键项目 → Gradle → Reload Gradle Project
- **命令行**：`./gradlew clean build --refresh-dependencies`

**解决方案 2：检查仓库配置**

确保 `repositories` 块在 `dependencies` 块**之前**：

```kotlin
repositories {
    maven("https://maven.wcpe.top/repository/maven-public/")
    mavenCentral()
}

dependencies {
    compileOnly("top.wcpe.mc.plugin:multicurrencyeconomy-api:VERSION")
}
```

**解决方案 3：验证仓库连接**

测试仓库是否可访问：
```bash
curl -I https://maven.wcpe.top/repository/maven-public/
```

---

### 7.4 问题：运行时 NullPointerException

**症状：**
```kotlin
operator!!.getBalance(...)  // NPE: operator is null
```

**解决方案：增加空检查**

```kotlin
fun getBalance(player: Player, currencyId: String): BigDecimal {
    // ✅ 推荐：安全调用
    return operator?.getBalance(player.name, currencyId) ?: BigDecimal.ZERO
    
    // ❌ 避免：强制非空断言
    // return operator!!.getBalance(player.name, currencyId)
}
```

**完整示例（带初始化检查）：**

```kotlin
object MultiCurrencyEconomyHook {
    private var operator: EconomyOperator? = null
    private var available = false
    
    fun init() {
        if (!MultiCurrencyEconomyApi.isReady()) {
            available = false
            return
        }
        operator = MultiCurrencyEconomyApi.delegate("MyPlugin")
        available = true
    }
    
    fun getBalance(player: Player, currencyId: String): BigDecimal {
        if (!available || operator == null) {
            return BigDecimal.ZERO
        }
        return runCatching {
            operator!!.getBalance(player.name, currencyId)
        }.getOrDefault(BigDecimal.ZERO)
    }
}
```

---

### 7.5 问题：操作失败但无异常（success=false）

**诊断步骤：**

1. **检查返回的 message 字段**
   ```kotlin
   val result = operator.take(playerName, "coin", amount, reason)
   if (!result.success) {
       logger.warning("操作失败: ${result.message}")  // 查看失败原因
   }
   ```

2. **常见失败原因：**
   - `余额不足`：玩家余额 < 扣除金额
   - `找不到货币`：货币标识不存在或拼写错误
   - `货币已禁用`：该货币被管理员禁用
   - `金额必须为正数`：amount <= 0

3. **开启调试日志**
   - 在 MultiCurrencyEconomy 配置中启用 debug 模式
   - 查看详细操作日志

---

### 7.6 问题：如何验证集成是否成功

**测试清单：**

```kotlin
// ✅ 1. 验证 API 就绪
assert(MultiCurrencyEconomyApi.isReady())

// ✅ 2. 创建操作器
val operator = MultiCurrencyEconomyApi.delegate("TestPlugin")
assert(operator != null)

// ✅ 3. 查询货币列表
val currencies = MultiCurrencyEconomyApi.getActiveCurrencies()
assert(currencies.isNotEmpty())

// ✅ 4. 查询余额（不抛异常即为成功）
val balance = operator.getBalance("TestPlayer", "coin")
println("查询成功: balance = $balance")

// ✅ 5. 测试写操作
val result = operator.add("TestPlayer", "coin", BigDecimal("1.00"), "集成测试")
assert(result.success)
println("写操作成功: 新余额 = ${result.balance}")
```

**快速测试命令：**

在游戏内执行：
```
/eco balance 玩家名 货币ID        # 查询余额
/eco add 玩家名 货币ID 100         # 测试增加
/eco take 玩家名 货币ID 10         # 测试扣除
```

---

### 7.7 获取更多帮助

如果以上方法无法解决问题：

1. **查看完整日志**
   - 查看服务器 `logs/latest.log`
   - 搜索关键词：`MultiCurrencyEconomy`、`IllegalStateException`、`ClassNotFound`

2. **检查版本兼容性**
   - 服务器核心版本（Paper/Spigot 版本）
   - MultiCurrencyEconomy 插件版本
   - API 依赖版本

3. **提交问题反馈**
   - 附上完整错误堆栈
   - 说明服务器环境（核心类型、版本）
   - 提供 `build.gradle.kts` 配置