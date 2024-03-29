package io.novafoundation.nova.feature_dapp_api.presentation.common

import android.widget.ImageView
import coil.ImageLoader
import coil.load
import io.novafoundation.nova.feature_dapp_api.R

fun ImageView.showDAppIcon(
    url: String?,
    imageLoader: ImageLoader
) {
    if (url != null) {
        load(url, imageLoader)
    } else {
        setImageResource(R.drawable.ic_earth)
    }
}
