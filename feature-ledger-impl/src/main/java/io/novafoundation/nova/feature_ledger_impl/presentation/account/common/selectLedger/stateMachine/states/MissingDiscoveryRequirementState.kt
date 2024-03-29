package io.novafoundation.nova.feature_ledger_impl.presentation.account.common.selectLedger.stateMachine.states

import io.novafoundation.nova.common.utils.stateMachine.StateMachine
import io.novafoundation.nova.feature_ledger_impl.presentation.account.common.selectLedger.stateMachine.SelectLedgerEvent
import io.novafoundation.nova.feature_ledger_impl.presentation.account.common.selectLedger.stateMachine.SideEffect

enum class DiscoveryRequirement {
    BLUETOOTH, LOCATION
}

class MissingDiscoveryRequirementState(private val missingRequirements: Set<DiscoveryRequirement>) : SelectLedgerState() {

    override suspend fun StateMachine.Transition<SelectLedgerState, SideEffect>.performTransition(event: SelectLedgerEvent) {
        val newMissingRequirements = missingRequirements.updateByEvent(event) ?: return

        if (newMissingRequirements.isEmpty()) {
            startDiscovery()
        } else {
            missingDiscoveryState(newMissingRequirements)
        }
    }
}
