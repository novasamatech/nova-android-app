package io.novafoundation.nova.runtime.ext

import io.novafoundation.nova.common.data.network.runtime.binding.MultiAddress
import io.novafoundation.nova.common.data.network.runtime.binding.bindOrNull
import io.novafoundation.nova.common.utils.Modules
import io.novafoundation.nova.common.utils.emptyEthereumAccountId
import io.novafoundation.nova.common.utils.findIsInstanceOrNull
import io.novafoundation.nova.common.utils.formatNamed
import io.novafoundation.nova.common.utils.substrateAccountId
import io.novafoundation.nova.runtime.multiNetwork.chain.model.Chain
import io.novafoundation.nova.runtime.multiNetwork.chain.model.Chain.Asset.Type
import io.novafoundation.nova.runtime.multiNetwork.chain.model.ExplorerTemplateExtractor
import io.novafoundation.nova.runtime.multiNetwork.chain.model.FullChainAssetId
import io.novafoundation.nova.runtime.multiNetwork.chain.model.TypesUsage
import jp.co.soramitsu.fearless_utils.extensions.asEthereumAccountId
import jp.co.soramitsu.fearless_utils.extensions.asEthereumAddress
import jp.co.soramitsu.fearless_utils.extensions.asEthereumPublicKey
import jp.co.soramitsu.fearless_utils.extensions.isValid
import jp.co.soramitsu.fearless_utils.extensions.toAccountId
import jp.co.soramitsu.fearless_utils.extensions.toAddress
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHexUntyped
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.addressPrefix
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress

val Chain.typesUsage: TypesUsage
    get() = when {
        types == null -> TypesUsage.BASE
        types.overridesCommon -> TypesUsage.OWN
        else -> TypesUsage.BOTH
    }

val Chain.utilityAsset
    get() = assets.first(Chain.Asset::isUtilityAsset)

val Chain.isSubstrateBased
    get() = !isEthereumBased

val Chain.commissionAsset
    get() = utilityAsset

inline fun <reified T : Chain.ExternalApi> Chain.externalApi(): T? {
    return externalApis.findIsInstanceOrNull<T>()
}

val Chain.Asset.isUtilityAsset: Boolean
    get() = id == 0

private const val MOONBEAM_XC_PREFIX = "xc"

fun Chain.Asset.unifiedSymbol(): String {
    return symbol.removePrefix(MOONBEAM_XC_PREFIX)
}

val Chain.Asset.disabled: Boolean
    get() = !enabled

val Chain.genesisHash: String
    get() = id

fun Chain.addressOf(accountId: ByteArray): String {
    return if (isEthereumBased) {
        accountId.toEthereumAddress()
    } else {
        accountId.toAddress(addressPrefix.toShort())
    }
}

fun ByteArray.toEthereumAddress(): String {
    return asEthereumAccountId().toAddress(withChecksum = true).value
}

fun Chain.accountIdOf(address: String): ByteArray {
    return if (isEthereumBased) {
        address.asEthereumAddress().toAccountId().value
    } else {
        address.toAccountId()
    }
}

fun Chain.accountIdOrNull(address: String): ByteArray? {
    return runCatching { accountIdOf(address) }.getOrNull()
}

fun Chain.emptyAccountId() = if (isEthereumBased) {
    emptyEthereumAccountId()
} else {
    ByteArray(32)
}

fun Chain.accountIdOrDefault(maybeAddress: String): ByteArray {
    return accountIdOrNull(maybeAddress) ?: emptyAccountId()
}

fun Chain.accountIdOf(publicKey: ByteArray): ByteArray {
    return if (isEthereumBased) {
        publicKey.asEthereumPublicKey().toAccountId().value
    } else {
        publicKey.substrateAccountId()
    }
}

fun Chain.hexAccountIdOf(address: String): String {
    return accountIdOf(address).toHexString()
}

fun Chain.multiAddressOf(accountId: ByteArray): MultiAddress {
    return if (isEthereumBased) {
        MultiAddress.Address20(accountId)
    } else {
        MultiAddress.Id(accountId)
    }
}

fun Chain.isValidAddress(address: String): Boolean {
    return runCatching {
        if (isEthereumBased) {
            address.asEthereumAddress().isValid()
        } else {
            address.toAccountId() // verify supplied address can be converted to account id

            address.addressPrefix() == addressPrefix.toShort()
        }
    }.getOrDefault(false)
}

fun Chain.isValidEvmAddress(address: String): Boolean {
    return runCatching {
        if (isEthereumBased) {
            address.asEthereumAddress().isValid()
        } else {
            false
        }
    }.getOrDefault(false)
}

val Chain.isParachain
    get() = parentId != null

fun Chain.multiAddressOf(address: String): MultiAddress = multiAddressOf(accountIdOf(address))

fun Chain.availableExplorersFor(field: ExplorerTemplateExtractor) = explorers.filter { field(it) != null }

fun Chain.Explorer.accountUrlOf(address: String): String {
    return format(Chain.Explorer::account, "address", address)
}

fun Chain.Explorer.extrinsicUrlOf(extrinsicHash: String): String {
    return format(Chain.Explorer::extrinsic, "hash", extrinsicHash)
}

fun Chain.Explorer.eventUrlOf(eventId: String): String {
    return format(Chain.Explorer::event, "event", eventId)
}

private inline fun Chain.Explorer.format(
    templateExtractor: ExplorerTemplateExtractor,
    argumentName: String,
    argumentValue: String,
): String {
    val template = templateExtractor(this) ?: throw Exception("Cannot find template in the chain explorer: $name")

    return template.formatNamed(argumentName to argumentValue)
}

object ChainGeneses {

    const val KUSAMA = "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe"
    const val POLKADOT = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
    const val STATEMINE = "48239ef607d7928874027a43a67689209727dfb3d3dc5e5b03a39bdc2eda771a"

    const val ACALA = "fc41b9bd8ef8fe53d58c7ea67c794c7ec9a73daf05e6d54b14ff6342c99ba64c"

    const val ROCOCO_ACALA = "a84b46a3e602245284bb9a72c4abd58ee979aa7a5d7f8c4dfdddfaaf0665a4ae"

    const val STATEMINT = "68d56f15f85d3136970ec16946040bc1752654e906147f7e43e9d539d7c3de2f"
    const val EDGEWARE = "742a2ca70c2fda6cee4f8df98d64c4c670a052d9568058982dad9d5a7a135c5b"

    const val KARURA = "baf5aabe40646d11f0ee8abbdc64f4a4b7674925cba08e4a05ff9ebed6e2126b"

    const val NODLE_PARACHAIN = "97da7ede98d7bad4e36b4d734b6055425a3be036da2a332ea5a7037656427a21"

    const val MOONBEAM = "fe58ea77779b7abda7da4ec526d14db9b1e9cd40a217c34892af80a9b332b76d"
    const val MOONRIVER = "401a1f9dca3da46f5c4091016c8a2f26dcea05865116b286f60f668207d1474b"

    const val POLYMESH = "6fbd74e5e1d0a61d52ccfe9d4adaed16dd3a7caa37c6bc4d0c2fa12e8b2f4063"
    const val CENTRIFUGE = "b3db41421702df9a7fcac62b53ffeac85f7853cc4e689e0b93aeb3db18c09d82"

    const val XX_NETWORK = "50dd5d206917bf10502c68fb4d18a59fc8aa31586f4e8856b493e43544aa82aa"
}

val Chain.Companion.Geneses
    get() = ChainGeneses

fun Chain.Asset.requireStatemine(): Type.Statemine {
    require(type is Type.Statemine)

    return type
}

fun Type.Statemine.palletNameOrDefault(): String {
    return palletName ?: Modules.ASSETS
}

fun Chain.Asset.requireOrml(): Type.Orml {
    require(type is Type.Orml)

    return type
}

fun Chain.Asset.requireErc20(): Type.Evm {
    require(type is Type.Evm)

    return type
}

fun Chain.Asset.ormlCurrencyId(runtime: RuntimeSnapshot): Any? {
    val ormlType = requireOrml()

    val currencyIdType = runtime.typeRegistry[ormlType.currencyIdType]
        ?: error("Cannot find type ${ormlType.currencyIdType}")

    return currencyIdType.fromHex(runtime, ormlType.currencyIdScale)
}

val Chain.Asset.fullId: FullChainAssetId
    get() = FullChainAssetId(chainId, id)

fun Chain.enabledAssets(): List<Chain.Asset> = assets.filter { it.enabled }

fun Chain.disabledAssets(): List<Chain.Asset> = assets.filterNot { it.enabled }

fun Chain.findAssetByOrmlCurrencyId(runtime: RuntimeSnapshot, currencyId: Any?): Chain.Asset? {
    return assets.find { asset ->
        if (asset.type !is Type.Orml) return@find false
        val currencyType = runtime.typeRegistry[asset.type.currencyIdType] ?: return@find false

        val currencyIdScale = bindOrNull { currencyType.toHexUntyped(runtime, currencyId) } ?: return@find false

        currencyIdScale == asset.type.currencyIdScale
    }
}
