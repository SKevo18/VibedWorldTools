package org.waste.of.time

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer
import net.minecraft.SharedConstants
import net.minecraft.client.MinecraftClient
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.waste.of.time.config.WorldToolsConfig

object WorldTools {
    const val MOD_NAME = "WorldTools"
    const val MOD_ID = "worldtools"
    private const val URL = "https://github.com/Avanatiker/WorldTools/"
    const val MCA_EXTENSION = ".mca"
    const val DAT_EXTENSION = ".dat"
    const val MAX_LEVEL_NAME_LENGTH = 64
    const val TIMESTAMP_KEY = "CaptureTimestamp"
    val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    // Data fixer version. If unavailable via API, fall back to 0 which is acceptable for client-side exports.
    val CURRENT_VERSION = 0
    private val VERSION: String = LoaderInfo.getVersion()
    val CREDIT_MESSAGE = "This file was created by $MOD_NAME $VERSION ($URL)"
    val CREDIT_MESSAGE_MD = "This file was created by [$MOD_NAME $VERSION]($URL)"
    val LOG: Logger = LogManager.getLogger()

    // KeyBindings are initialized by the platform-specific module (Fabric/Forge)
    lateinit var CAPTURE_KEY: Any
    lateinit var CONFIG_KEY: Any

    val mc: MinecraftClient = MinecraftClient.getInstance()
    lateinit var config: WorldToolsConfig; private set

    fun initialize() {
        LOG.info("Initializing $MOD_NAME $VERSION")
        AutoConfig.register(WorldToolsConfig::class.java, ::GsonConfigSerializer)
        config = AutoConfig.getConfigHolder(WorldToolsConfig::class.java).config
    }
}
