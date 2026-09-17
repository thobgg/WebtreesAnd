package de.bgghome.webtrees.nativ

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import de.bgghome.webtrees.nativ.api.WtClient
import de.bgghome.webtrees.nativ.data.Settings

class WtApp : Application(), ImageLoaderFactory {

    lateinit var client: WtClient
        private set
    lateinit var settings: Settings
        private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        client = WtClient(this).also { it.baseUrl = settings.baseUrl }
    }

    // Bilder sind signierte webtrees-Routen und brauchen dieselbe Sitzung (Cookie) wie die API.
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(client.http)
            .crossfade(true)
            .build()
}
