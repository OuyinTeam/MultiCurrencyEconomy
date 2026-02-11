package top.wcpe.mc.plugin.multicurrencyeconomy.api.model

/**
 * 货币信息只读 DTO — 对外暴露货币的基本属性。
 *
 * 【用途】第三方插件通过 API 获取货币元数据，不包含内部管理字段（如软删除标记）。
 * 【线程语义】不可变数据类，线程安全，可在任意线程传递与读取。
 * 【注意事项】precision 字段决定了该货币金额的小数位数，所有金额展示应按此精度格式化。
 */
data class CurrencyInfo(

    /** 货币唯一 ID（内部 UUID） */
    val id: String,

    /** 货币标识符（英文小写，如 "coin"、"point"），全局唯一业务键 */
    val identifier: String,

    /** 货币显示名称（如 "金币"、"点券"），用于 GUI/消息展示 */
    val displayName: String,

    /** 货币符号（如 "☆"、"$"），用于余额格式化前缀 */
    val symbol: String,

    /**
     * 精度 — 允许的小数位数。
     * 0 = 仅整数（适用于点券等不可分割的货币）；
     * 2 = 两位小数（如金币 1.23）。
     */
    val precision: Int,

    /** 默认余额上限（-1 = 不限；正数为上限） */
    val defaultMaxBalance: Long,

    /** 是否为主货币 — Vault Economy 默认操作此货币 */
    val primary: Boolean,

    /** 是否启用 — 禁用时不允许余额变更但可查询 */
    val enabled: Boolean
)
