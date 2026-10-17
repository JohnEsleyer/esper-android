package com.esper.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.esper.android.ui.theme.DataUriFetcher

class EsperApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(DataUriFetcher.Factory())
                add(SvgDecoder.Factory())
            }
            .build()
    }
}
