package io.novafoundation.nova.app.root.navigation.account

import io.novafoundation.nova.app.root.navigation.BaseInterScreenCommunicator
import io.novafoundation.nova.app.root.navigation.NavigationHolder
import io.novafoundation.nova.feature_account_api.presenatation.account.wallet.list.SelectAddressCommunicator
import io.novafoundation.nova.feature_account_api.presenatation.account.wallet.list.SelectAddressForTransactionRequester
import io.novafoundation.nova.feature_account_api.presenatation.account.wallet.list.SelectAddressForTransactionResponder
import io.novafoundation.nova.feature_account_impl.presentation.account.list.selecting.SelectAddressFragment
import io.novafoundation.nova.feature_assets.presentation.AssetsRouter

class SelectAddressCommunicatorImpl(private val router: AssetsRouter, navigationHolder: NavigationHolder) :
    BaseInterScreenCommunicator<SelectAddressForTransactionRequester.Request, SelectAddressForTransactionResponder.Response>(navigationHolder),
    SelectAddressCommunicator {

    override fun openRequest(request: SelectAddressForTransactionRequester.Request) {
        router.openSelectAddress(SelectAddressFragment.getBundle(request))
    }
}
