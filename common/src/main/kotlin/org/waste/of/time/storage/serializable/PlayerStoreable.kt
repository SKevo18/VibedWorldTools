package org.waste.of.time.storage.serializable

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.Inventories
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtIo
import net.minecraft.text.MutableText
import net.minecraft.util.Util
import net.minecraft.util.WorldSavePath
import net.minecraft.util.collection.DefaultedList
import net.minecraft.world.level.storage.LevelStorage.Session
import org.waste.of.time.Utils.asString
import org.waste.of.time.WorldTools
import org.waste.of.time.WorldTools.config
import org.waste.of.time.manager.MessageManager.translateHighlight
import org.waste.of.time.manager.StatisticManager
import org.waste.of.time.storage.Cacheable
import org.waste.of.time.storage.CustomRegionBasedStorage
import org.waste.of.time.storage.Storeable
import org.waste.of.time.storage.cache.HotCache
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

data class PlayerStoreable(
    val player: PlayerEntity
) : Cacheable, Storeable() {
    override fun shouldStore() = config.general.capture.players

    override val verboseInfo: MutableText
        get() = translateHighlight(
            "worldtools.capture.saved.player",
            player.name,
            player.pos.asString(),
            player.world.registryKey.value.path
        )

    override val anonymizedInfo: MutableText
        get() = translateHighlight(
            "worldtools.capture.saved.player.anonymized",
            player.name,
            player.world.registryKey.value.path
        )

    override fun cache() {
        HotCache.players.add(this)
    }

    override fun flush() {
        HotCache.players.remove(this)
    }

    override fun store(session: Session, cachedStorages: MutableMap<String, CustomRegionBasedStorage>) {
        savePlayerData(player, session)
        session.createSaveHandler()
        StatisticManager.players++
        StatisticManager.dimensions.add(player.world.registryKey.value.path)
    }

    private fun savePlayerInventory(player: PlayerEntity, playerTag: NbtCompound) {
        val inventory = player.inventory
        val inventoryList = net.minecraft.nbt.NbtList()
        val registryOps = player.world.registryManager.getOps(net.minecraft.nbt.NbtOps.INSTANCE)

        // Save main inventory, armor, and offhand
        for (i in 0 until inventory.size()) {
            val stack = inventory.getStack(i)
            if (!stack.isEmpty) {
                // Use VALIDATED_CODEC for proper validation
                val encoded = ItemStack.VALIDATED_CODEC.encodeStart(registryOps, stack)
                encoded.resultOrPartial { error ->
                    WorldTools.LOG.warn("Failed to encode item stack at slot $i: $error")
                }.ifPresent { nbtElement ->
                    // ItemStack codec encodes to NbtCompound
                    val itemNbt = nbtElement as? NbtCompound ?: return@ifPresent
                    itemNbt.putByte("Slot", i.toByte())
                    inventoryList.add(itemNbt)
                }
            }
        }
        playerTag.put("Inventory", inventoryList)

        // Save enderchest
        val enderChest = player.enderChestInventory
        val enderList = net.minecraft.nbt.NbtList()
        WorldTools.LOG.info("Saving enderchest for ${player.name.string}: size=${enderChest.size()}")

        var savedCount = 0
        for (i in 0 until enderChest.size()) {
            val stack = enderChest.getStack(i)
            WorldTools.LOG.info("  Slot $i: ${if (stack.isEmpty) "empty" else "${stack.count}x ${stack.item}"}")
            if (!stack.isEmpty) {
                val encoded = ItemStack.VALIDATED_CODEC.encodeStart(registryOps, stack)
                encoded.resultOrPartial { error ->
                    WorldTools.LOG.warn("Failed to encode enderchest item at slot $i: $error")
                }.ifPresent { nbtElement ->
                    val itemNbt = nbtElement as? NbtCompound ?: return@ifPresent
                    itemNbt.putByte("Slot", i.toByte())
                    enderList.add(itemNbt)
                    savedCount++
                }
            }
        }
        WorldTools.LOG.info("Saved $savedCount enderchest items to NBT")
        playerTag.put("EnderItems", enderList)
    }

    private fun savePlayerData(player: PlayerEntity, session: Session) {
        try {
            val playerDataDir = session.getDirectory(WorldSavePath.PLAYERDATA).toFile()
            playerDataDir.mkdirs()

            val newPlayerFile = File.createTempFile(player.uuidAsString + "-", ".dat", playerDataDir).toPath()
            val playerTag = NbtCompound()

            // Manually save all important player data
            // Save UUID as int array (vanilla format)
            val uuid = player.uuid
            playerTag.putIntArray("UUID", intArrayOf(
                (uuid.mostSignificantBits shr 32).toInt(),
                uuid.mostSignificantBits.toInt(),
                (uuid.leastSignificantBits shr 32).toInt(),
                uuid.leastSignificantBits.toInt()
            ))

            // Save player position
            playerTag.put("Pos", net.minecraft.nbt.NbtList().apply {
                add(net.minecraft.nbt.NbtDouble.of(player.x))
                add(net.minecraft.nbt.NbtDouble.of(player.y))
                add(net.minecraft.nbt.NbtDouble.of(player.z))
            })

            // Save player rotation
            playerTag.put("Rotation", net.minecraft.nbt.NbtList().apply {
                add(net.minecraft.nbt.NbtFloat.of(player.yaw))
                add(net.minecraft.nbt.NbtFloat.of(player.pitch))
            })

            // Save player motion
            val velocity = player.velocity
            playerTag.put("Motion", net.minecraft.nbt.NbtList().apply {
                add(net.minecraft.nbt.NbtDouble.of(velocity.x))
                add(net.minecraft.nbt.NbtDouble.of(velocity.y))
                add(net.minecraft.nbt.NbtDouble.of(velocity.z))
            })

            // Save dimension
            playerTag.putString("Dimension", player.world.registryKey.value.toString())

            // Save health and food
            playerTag.putFloat("Health", player.health)
            playerTag.putInt("foodLevel", player.hungerManager.foodLevel)
            playerTag.putFloat("foodSaturationLevel", player.hungerManager.saturationLevel)

            // Save XP
            playerTag.putInt("XpLevel", player.experienceLevel)
            playerTag.putFloat("XpP", player.experienceProgress)
            playerTag.putInt("XpTotal", player.totalExperience)

            // Save game mode - default to survival
            playerTag.putInt("playerGameType", 0)

            // Save air, fire, and ground status
            playerTag.putShort("Air", player.air.toShort())
            playerTag.putShort("Fire", player.fireTicks.toShort())
            playerTag.putBoolean("OnGround", player.isOnGround)
            playerTag.putBoolean("Invulnerable", player.isInvulnerable)
            playerTag.putInt("PortalCooldown", player.portalCooldown)
            playerTag.putFloat("FallDistance", player.fallDistance.toFloat())

            // Save abilities
            val abilities = NbtCompound()
            abilities.putBoolean("invulnerable", player.abilities.invulnerable)
            abilities.putBoolean("flying", player.abilities.flying)
            abilities.putBoolean("mayfly", player.abilities.allowFlying)
            abilities.putBoolean("instabuild", player.abilities.creativeMode)
            abilities.putFloat("flySpeed", player.abilities.flySpeed)
            abilities.putFloat("walkSpeed", player.abilities.walkSpeed)
            playerTag.put("abilities", abilities)

            // Save score
            playerTag.putInt("Score", player.score)

            // Save inventory and enderchest with our properly encoded versions
            savePlayerInventory(player, playerTag)

            WorldTools.LOG.info("Saved player ${player.name.string} with ${playerTag.keys.size} NBT keys")

            if (config.entity.censor.lastDeathLocation) {
                playerTag.remove("LastDeathLocation")
            }
            NbtIo.writeCompressed(playerTag, newPlayerFile)
            val currentFile = File(playerDataDir, player.uuidAsString + ".dat").toPath()
            val backupFile = File(playerDataDir, player.uuidAsString + ".dat_old").toPath()
            Util.backupAndReplace(currentFile, newPlayerFile, backupFile)
        } catch (e: Exception) {
            WorldTools.LOG.warn("Failed to save player data for {}", player.name.string)
        }
    }
}
