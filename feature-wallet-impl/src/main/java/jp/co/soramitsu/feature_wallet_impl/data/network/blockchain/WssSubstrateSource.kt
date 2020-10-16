@file:Suppress("EXPERIMENTAL_API_USAGE")

package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain

import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.rpc.DeliveryType
import jp.co.soramitsu.common.data.network.rpc.SocketService
import jp.co.soramitsu.common.data.network.rpc.mappers.nonNull
import jp.co.soramitsu.common.data.network.rpc.mappers.pojo
import jp.co.soramitsu.common.data.network.rpc.mappers.scale
import jp.co.soramitsu.common.data.network.rpc.mappers.scaleCollection
import jp.co.soramitsu.common.data.network.rpc.mappers.string
import jp.co.soramitsu.common.data.network.rpc.subscription.SubscriptionChange
import jp.co.soramitsu.common.data.network.scale.EncodableStruct
import jp.co.soramitsu.common.data.network.scale.invoke
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.ss58.AddressType
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.account.AccountInfoRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.author.PendingExtrinsicsRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersionRequest
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.extrinsics.TransferRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.extrinsics.signExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests.FeeCalculationRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests.GetBlockRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.requests.SubscribeStorageRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.FeeRemote
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.FeeResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.RuntimeVersion
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.SignedBlock
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.response.StorageChange
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountData
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountData.feeFrozen
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountData.free
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountData.miscFrozen
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountData.reserved
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountInfo
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountInfo.data
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountInfo.nonce
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.AccountInfo.refCount
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.Call
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.Call.args
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.CallStub
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.ExtrinsicPayloadValue
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.ExtrinsicStub
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SignedExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SignedExtrinsic.accountId
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SignedExtrinsic.call
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SignedExtrinsic.signature
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SignedExtrinsic.signatureVersion
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SignedExtrinsicStub
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SubmittableExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SubmittableExtrinsic.byteLength
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SubmittableExtrinsic.signedExtrinsic
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SupportedCall
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.TransferArgs
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.TransferArgs.recipientId
import org.spongycastle.util.encoders.Hex
import java.math.BigInteger

class WssSubstrateSource(
    private val socketService: SocketService,
    private val signer: Signer,
    private val keypairFactory: KeypairFactory,
    private val sS58Encoder: SS58Encoder
) : SubstrateRemoteSource {

    override fun fetchAccountInfo(account: Account): Single<EncodableStruct<AccountInfo>> {
        val publicKeyBytes = extractPublicKeyBytes(account)
        val request = AccountInfoRequest(publicKeyBytes)

        return socketService.executeRequest(request, responseType = scale(AccountInfo))
            .map { response -> response.result ?: emptyAccountInfo() }
    }

    override fun getTransferFee(account: Account, transfer: Transfer): Single<FeeResponse> {
        return generateFakeKeyPair(account).flatMap { keypair ->
            buildSubmittableExtrinsic(account, transfer, keypair)
        }.flatMap { (extrinsic, newAccountInfo) ->
            val request = FeeCalculationRequest(extrinsic)

            socketService.executeRequest(request, responseType = pojo<FeeRemote>().nonNull()).map { feeRemote ->
                FeeResponse(feeRemote, newAccountInfo)
            }
        }
    }

    override fun performTransfer(
        account: Account,
        transfer: Transfer,
        keypair: Keypair
    ): Single<String> {
        return buildSubmittableExtrinsic(account, transfer, keypair).map { (extrinsic, _) ->
            TransferRequest(extrinsic)
        }.flatMap { transferRequest ->
            socketService.executeRequest(transferRequest,
                responseType = string().nonNull(),
                deliveryType = DeliveryType.AT_MOST_ONCE
            )
        }
    }

    override fun listenForAccountUpdates(account: Account): Observable<StorageChange> {
        val request = SubscribeStorageRequest(extractPublicKeyBytes(account))

        return socketService.subscribe(request)
            .map(::buildStorageChange)
    }

    override fun fetchAccountTransactionInBlock(blockHash: String, account: Account) : Single<List<EncodableStruct<SubmittableExtrinsic>>> {
        val request = GetBlockRequest(blockHash)

        return socketService.executeRequest(request, responseType = pojo<SignedBlock>().nonNull())
            .map { block -> filterAccountTransactions(account, block.block.extrinsics) }
    }

    private fun buildStorageChange(subscriptionChange: SubscriptionChange): StorageChange {
        val block = subscriptionChange.params.result.block

        // changes are in format [[storage key, account info], [..], ..]
        val changes = subscriptionChange.params.result.changes as List<List<String?>>

        // only interested in one account
        val encodedAccountInfo = changes.first()[1]

        val accountInfo = if (encodedAccountInfo != null) AccountInfo.read(encodedAccountInfo) else emptyAccountInfo()

        return StorageChange(block, accountInfo)
    }

    private fun extractPublicKeyBytes(account: Account): ByteArray {
        val publicKey = account.publicKey

        return Hex.decode(publicKey)
    }

    private fun buildSubmittableExtrinsic(
        account: Account,
        transfer: Transfer,
        keypair: Keypair
    ): Single<Pair<EncodableStruct<SubmittableExtrinsic>, EncodableStruct<AccountInfo>>> {
        return getRuntimeVersion().flatMap { runtimeInfo ->
            val cryptoType = mapCryptoTypeToEncryption(account.cryptoType)
            val accountIdValue = Hex.decode(account.publicKey)

            getNonce(account).map { (currentNonce, newAccountInfo) ->
                val genesis = account.network.type.genesisHash
                val genesisBytes = Hex.decode(genesis)

                val callStruct = createTransferCall(account.network.type, transfer.recipient, transfer.amountInPlanks)

                val payload = ExtrinsicPayloadValue { payload ->
                    payload[ExtrinsicPayloadValue.call] = callStruct
                    payload[ExtrinsicPayloadValue.nonce] = currentNonce
                    payload[ExtrinsicPayloadValue.specVersion] = runtimeInfo.specVersion.toUInt()
                    payload[ExtrinsicPayloadValue.transactionVersion] = runtimeInfo.transactionVersion.toUInt()

                    payload[ExtrinsicPayloadValue.genesis] = genesisBytes
                    payload[ExtrinsicPayloadValue.blockHash] = genesisBytes
                }

                val signatureValue = signer.signExtrinsic(payload, keypair, cryptoType)

                val extrinsic = SignedExtrinsic { extrinsic ->
                    extrinsic[accountId] = accountIdValue
                    extrinsic[signature] = signatureValue
                    extrinsic[signatureVersion] = cryptoType.signatureVersion.toUByte()
                    extrinsic[SignedExtrinsic.nonce] = currentNonce
                    extrinsic[call] = callStruct
                }

                val extrinsicBytes = SignedExtrinsic.toByteArray(extrinsic)
                val byteLengthValue = extrinsicBytes.size.toBigInteger()

                val submittableExtrinsic = SubmittableExtrinsic { struct ->
                    struct[byteLength] = byteLengthValue
                    struct[signedExtrinsic] = extrinsic
                }

                submittableExtrinsic to newAccountInfo
            }
        }
    }

    private fun createTransferCall(
        networkType: Node.NetworkType,
        recipientAddress: String,
        amount: BigInteger
    ): EncodableStruct<Call> {
        val addressType = mapNetworkTypeToAddressType(networkType)

        return Call { call ->
            call[Call.callIndex] = Pair(4.toUByte(), 0.toUByte())

            call[Call.args] = TransferArgs { args ->
                args[TransferArgs.recipientId] = sS58Encoder.decode(recipientAddress, addressType)
                args[TransferArgs.amount] = amount
            }
        }
    }

    private fun getNonce(account: Account): Single<Pair<BigInteger, EncodableStruct<AccountInfo>>> {
        return fetchAccountInfo(account).flatMap { accountInfo ->
            val accountNonce = accountInfo[nonce]

            getPendingExtrinsicsCount(account).map { pendingExtrinsics ->
                val result = accountNonce + pendingExtrinsics.toUInt()

                result.toLong().toBigInteger() to accountInfo
            }
        }
    }

    private fun generateFakeKeyPair(account: Account) = Single.fromCallable {
        val cryptoType = mapCryptoTypeToEncryption(account.cryptoType)
        val emptySeed = ByteArray(32)
        keypairFactory.generate(cryptoType, emptySeed, "")
    }

    private fun getPendingExtrinsicsCount(account: Account): Single<Int> {
        val request = PendingExtrinsicsRequest()

        return socketService.executeRequest(request, scaleCollection(SubmittableExtrinsic))
            .map { it.result ?: throw IllegalArgumentException("Result is null") }
            .map { countUserExtrinsics(account, it) }
    }

    private fun countUserExtrinsics(account: Account, list: List<EncodableStruct<SubmittableExtrinsic>>): Int {
        val publicKeyBytes = Hex.decode(account.publicKey)

        return list.count { it[signedExtrinsic][accountId].contentEquals(publicKeyBytes) }
    }

    private fun getRuntimeVersion(): Single<RuntimeVersion> {
        val request = RuntimeVersionRequest()

        return socketService.executeRequest(request, pojo<RuntimeVersion>().nonNull())
    }

    private fun emptyAccountInfo() = AccountInfo { info ->
        info[nonce] = 0.toUInt()
        info[refCount] = 0.toUInt()

        info[data] = AccountData { data ->
            data[free] = 0.toBigInteger()
            data[reserved] = 0.toBigInteger()
            data[miscFrozen] = 0.toBigInteger()
            data[feeFrozen] = 0.toBigInteger()
        }
    }

    private fun mapCryptoTypeToEncryption(cryptoType: CryptoType): EncryptionType {
        return when (cryptoType) {
            CryptoType.SR25519 -> EncryptionType.SR25519
            CryptoType.ED25519 -> EncryptionType.ED25519
            CryptoType.ECDSA -> EncryptionType.ECDSA
        }
    }

    private fun mapNetworkTypeToAddressType(networkType: Node.NetworkType): AddressType {
        return when (networkType) {
            Node.NetworkType.KUSAMA -> AddressType.KUSAMA
            Node.NetworkType.POLKADOT -> AddressType.POLKADOT
            Node.NetworkType.WESTEND -> AddressType.WESTEND
        }
    }

    private fun filterAccountTransactions(account: Account, extrinsics: List<String>): List<EncodableStruct<SubmittableExtrinsic>> {
        val currentPublicKey = extractPublicKeyBytes(account)

        return extrinsics.filter { hex ->
            val stub = ExtrinsicStub.read(hex)

            val callIndex = stub[ExtrinsicStub.signedExtrinsic][SignedExtrinsicStub.call][CallStub.callIndex]
            val call = SupportedCall.from(callIndex)

            call != null && call == SupportedCall.TRANSFER
        }
            .map(SubmittableExtrinsic::read)
            .filter { transfer ->
                val signed = transfer[signedExtrinsic]
                val sender = signed[accountId]
                val receiver = signed[call][args][recipientId]

                sender.contentEquals(currentPublicKey) || receiver.contentEquals(currentPublicKey)
            }
    }
}