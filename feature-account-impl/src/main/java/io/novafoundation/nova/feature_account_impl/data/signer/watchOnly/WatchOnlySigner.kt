package io.novafoundation.nova.feature_account_impl.data.signer.watchOnly

import io.novafoundation.nova.common.base.errors.SigningCancelledException
import io.novafoundation.nova.feature_account_api.presenatation.account.watchOnly.WatchOnlyMissingKeysPresenter
import jp.co.soramitsu.fearless_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.signer.Signer
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.signer.SignerPayloadExtrinsic
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.signer.SignerPayloadRaw

class WatchOnlySigner(
    private val watchOnlySigningPresenter: WatchOnlyMissingKeysPresenter
) : Signer {

    override suspend fun signExtrinsic(payloadExtrinsic: SignerPayloadExtrinsic): SignatureWrapper {
        cannotSign()
    }

    override suspend fun signRaw(payload: SignerPayloadRaw): SignatureWrapper {
        cannotSign()
    }

    private suspend fun cannotSign(): Nothing {
        watchOnlySigningPresenter.presentNoKeysFound()

        throw SigningCancelledException()
    }
}
