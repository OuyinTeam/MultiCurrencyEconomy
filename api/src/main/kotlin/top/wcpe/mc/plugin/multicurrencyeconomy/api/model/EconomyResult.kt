package top.wcpe.mc.plugin.multicurrencyeconomy.api.model

import java.math.BigDecimal

/**
 * 经济操作结果 — 封装余额变更操作的返回信息。
 *
 * 【用途】替代简单的 Boolean 返回值，为调用方提供更丰富的操作反馈。
 * 【线程语义】不可变数据类，线程安全，可在任意线程传递与读取。
 */
data class EconomyResult(

    /** 操作是否成功 */
    val success: Boolean,

    /** 操作后的新余额（操作失败时为操作前余额） */
    val balance: BigDecimal,

    /** 操作描述/错误信息 */
    val message: String = ""
) {
    companion object {
        /**
         * 快速创建成功结果。
         *
         * @param balance 操作后新余额
         * @param message 成功描述（可选）
         * @return 成功的 EconomyResult
         */
        fun success(balance: BigDecimal, message: String = ""): EconomyResult {
            return EconomyResult(true, balance, message)
        }

        /**
         * 快速创建失败结果。
         *
         * @param balance 当前余额（未变更）
         * @param message 失败原因
         * @return 失败的 EconomyResult
         */
        fun failure(balance: BigDecimal, message: String): EconomyResult {
            return EconomyResult(false, balance, message)
        }
    }
}
