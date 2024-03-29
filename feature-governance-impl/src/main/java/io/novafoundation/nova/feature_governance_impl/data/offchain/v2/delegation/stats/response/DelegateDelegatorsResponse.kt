package io.novafoundation.nova.feature_governance_impl.data.offchain.v2.delegation.stats.response

import com.google.gson.annotations.SerializedName
import io.novafoundation.nova.common.data.network.subquery.SubQueryNodes

class DelegateDelegatorsResponse(
    val delegations: SubQueryNodes<DelegatorRemote>
) {

    class DelegatorRemote(
        @SerializedName("delegator") val address: String,
        val delegation: VoteRemote
    )
}
