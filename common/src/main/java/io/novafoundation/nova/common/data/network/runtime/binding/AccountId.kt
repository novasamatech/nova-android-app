package io.novafoundation.nova.common.data.network.runtime.binding

import jp.co.soramitsu.fearless_utils.runtime.AccountId

@HelperBinding
fun bindAccountId(dynamicInstance: Any?): AccountId = dynamicInstance.cast()
fun bindNullableAccountId(dynamicInstance: Any?): AccountId? = dynamicInstance.nullableCast()
