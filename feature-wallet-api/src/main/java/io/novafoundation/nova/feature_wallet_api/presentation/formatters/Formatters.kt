package io.novafoundation.nova.feature_wallet_api.presentation.formatters

import io.novafoundation.nova.common.utils.formatting.format
import io.novafoundation.nova.feature_wallet_api.data.network.blockhain.types.Balance
import io.novafoundation.nova.feature_wallet_api.domain.model.amountFromPlanks
import io.novafoundation.nova.runtime.multiNetwork.chain.model.Chain
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

fun BigInteger.formatPlanks(chainAsset: Chain.Asset): String {
    return chainAsset.amountFromPlanks(this).formatTokenAmount(chainAsset)
}

fun ClosedRange<Balance>.formatPlanksRange(chainAsset: Chain.Asset): String {
    val startFormatted = chainAsset.amountFromPlanks(start).format()
    val endFormatted = endInclusive.formatPlanks(chainAsset)

    return "$startFormatted — $endFormatted"
}

fun BigDecimal.formatTokenAmount(chainAsset: Chain.Asset, roundingMode: RoundingMode = RoundingMode.FLOOR): String {
    return formatTokenAmount(chainAsset.symbol, roundingMode)
}

fun BigDecimal.formatTokenAmount(tokenSymbol: String, roundingMode: RoundingMode = RoundingMode.FLOOR): String {
    return "${format(roundingMode)} $tokenSymbol"
}

fun BigDecimal.formatTokenChange(chainAsset: Chain.Asset, isIncome: Boolean): String {
    val withoutSign = formatTokenAmount(chainAsset)
    val sign = if (isIncome) '+' else '-'

    return sign + withoutSign
}
