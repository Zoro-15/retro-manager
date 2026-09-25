package com.retropack.manager.runtime

import android.content.res.AssetManager
import com.retropack.domain.runtime.AssetSource
import java.io.IOException
import java.io.InputStream

/**
 * Adapts the Android [AssetManager] to the platform-free [AssetSource]
 * contract consumed by the domain-layer RuntimeProvisioner.
 */
class AndroidAssetSource(private val assets: AssetManager) : AssetSource {

    override fun list(path: String): List<String>? = try {
        assets.list(path)?.toList()
    } catch (_: IOException) {
        null
    }

    override fun open(path: String): InputStream = assets.open(path)
}
