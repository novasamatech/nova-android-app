package io.novafoundation.nova.runtime.multiNetwork

import com.google.gson.Gson
import io.novafoundation.nova.common.utils.diffed
import io.novafoundation.nova.common.utils.inBackground
import io.novafoundation.nova.common.utils.mapList
import io.novafoundation.nova.common.utils.removeHexPrefix
import io.novafoundation.nova.core.ethereum.Web3Api
import io.novafoundation.nova.core_db.dao.ChainDao
import io.novafoundation.nova.runtime.ethereum.Web3Api
import io.novafoundation.nova.runtime.ethereum.WebSocketWeb3jService
import io.novafoundation.nova.runtime.multiNetwork.asset.EvmAssetsSyncService
import io.novafoundation.nova.runtime.multiNetwork.chain.ChainSyncService
import io.novafoundation.nova.runtime.multiNetwork.chain.mappers.mapChainLocalToChain
import io.novafoundation.nova.runtime.multiNetwork.chain.model.Chain
import io.novafoundation.nova.runtime.multiNetwork.chain.model.ChainId
import io.novafoundation.nova.runtime.multiNetwork.chain.model.FullChainAssetId
import io.novafoundation.nova.runtime.multiNetwork.connection.ChainConnection
import io.novafoundation.nova.runtime.multiNetwork.connection.ConnectionPool
import io.novafoundation.nova.runtime.multiNetwork.runtime.RuntimeProvider
import io.novafoundation.nova.runtime.multiNetwork.runtime.RuntimeProviderPool
import io.novafoundation.nova.runtime.multiNetwork.runtime.RuntimeSubscriptionPool
import io.novafoundation.nova.runtime.multiNetwork.runtime.RuntimeSyncService
import io.novafoundation.nova.runtime.multiNetwork.runtime.types.BaseTypeSynchronizer
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

data class ChainService(
    val runtimeProvider: RuntimeProvider,
    val connection: ChainConnection
)

data class ChainWithAsset(
    val chain: Chain,
    val asset: Chain.Asset
)

@JvmInline
value class ChainsById(val value: Map<ChainId, Chain>) : Map<ChainId, Chain> by value {

    override operator fun get(key: ChainId): Chain? {
        return value[key.removeHexPrefix()]
    }
}

class ChainRegistry(
    private val runtimeProviderPool: RuntimeProviderPool,
    private val connectionPool: ConnectionPool,
    private val runtimeSubscriptionPool: RuntimeSubscriptionPool,
    private val chainDao: ChainDao,
    private val chainSyncService: ChainSyncService,
    private val evmAssetsSyncService: EvmAssetsSyncService,
    private val baseTypeSynchronizer: BaseTypeSynchronizer,
    private val runtimeSyncService: RuntimeSyncService,
    private val gson: Gson
) : CoroutineScope by CoroutineScope(Dispatchers.Default) {

    val currentChains = chainDao.joinChainInfoFlow()
        .mapList { mapChainLocalToChain(it, gson) }
        .diffed()
        .map { diff ->
            diff.removed.forEach {
                val chainId = it.id

                runtimeProviderPool.removeRuntimeProvider(chainId)
                runtimeSubscriptionPool.removeSubscription(chainId)
                runtimeSyncService.unregisterChain(chainId)
                connectionPool.removeConnection(chainId)
            }

            diff.newOrUpdated.forEach { chain ->
                val connection = connectionPool.setupConnection(chain)

                runtimeProviderPool.setupRuntimeProvider(chain)
                runtimeSyncService.registerChain(chain, connection)
                runtimeSubscriptionPool.setupRuntimeSubscription(chain, connection)
                runtimeProviderPool.setupRuntimeProvider(chain)
            }

            diff.all
        }
        .filter { it.isNotEmpty() }
        .distinctUntilChanged()
        .inBackground()
        .shareIn(this, SharingStarted.Eagerly, replay = 1)

    val chainsById = currentChains.map { chains -> chains.associateBy { it.id } }
        .inBackground()
        .shareIn(this, SharingStarted.Eagerly, replay = 1)

    init {
        launch {
            chainSyncService.syncUp()
            evmAssetsSyncService.syncUp()
        }

        baseTypeSynchronizer.sync()
    }

    fun getConnection(chainId: String) = connectionPool.getConnection(chainId.removeHexPrefix())

    fun getRuntimeProvider(chainId: String) = runtimeProviderPool.getRuntimeProvider(chainId.removeHexPrefix())

    suspend fun getChain(chainId: String): Chain = chainsById.first().getValue(chainId.removeHexPrefix())
}

suspend fun ChainRegistry.getChainOrNull(chainId: String): Chain? {
    return chainsById.first()[chainId.removeHexPrefix()]
}

suspend fun ChainRegistry.chainWithAssetOrNull(chainId: String, assetId: Int): ChainWithAsset? {
    val chain = getChainOrNull(chainId) ?: return null
    val chainAsset = chain.assetsById[assetId] ?: return null

    return ChainWithAsset(chain, chainAsset)
}

suspend fun ChainRegistry.chainWithAsset(chainId: String, assetId: Int): ChainWithAsset {
    val chain = chainsById.first().getValue(chainId)

    return ChainWithAsset(chain, chain.assetsById.getValue(assetId))
}

suspend fun ChainRegistry.asset(chainId: String, assetId: Int): Chain.Asset {
    val chain = chainsById.first().getValue(chainId)

    return chain.assetsById.getValue(assetId)
}
suspend fun ChainRegistry.asset(fullChainAssetId: FullChainAssetId): Chain.Asset {
    return asset(fullChainAssetId.chainId, fullChainAssetId.assetId)
}

suspend inline fun ChainRegistry.findChain(predicate: (Chain) -> Boolean): Chain? = currentChains.first().firstOrNull(predicate)
suspend inline fun ChainRegistry.findChains(predicate: (Chain) -> Boolean): List<Chain> = currentChains.first().filter(predicate)

suspend fun ChainRegistry.getRuntime(chainId: String) = getRuntimeProvider(chainId).get()

fun ChainRegistry.getSocket(chainId: String) = getConnection(chainId).socketService

suspend fun ChainRegistry.awaitChains() {
    chainsById.first()
}

suspend fun ChainRegistry.awaitSocket(chainId: String): SocketService {
    awaitChains()

    return getSocket(chainId)
}

suspend fun ChainRegistry.chainsById(): ChainsById = ChainsById(chainsById.first())

fun ChainRegistry.getService(chainId: String) = ChainService(
    runtimeProvider = getRuntimeProvider(chainId),
    connection = getConnection(chainId)
)

suspend fun ChainRegistry.ethereumApi(chainId: String): Web3Api {
    val socket = awaitSocket(chainId)

    return Web3Api(WebSocketWeb3jService(socket))
}
