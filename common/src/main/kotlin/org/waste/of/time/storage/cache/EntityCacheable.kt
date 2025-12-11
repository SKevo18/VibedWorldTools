package org.waste.of.time.storage.cache

import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.vehicle.VehicleInventory
import net.minecraft.inventory.Inventories
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtDouble
import net.minecraft.nbt.NbtFloat
import net.minecraft.nbt.NbtList
import org.waste.of.time.Utils.toByte
import org.waste.of.time.WorldTools.TIMESTAMP_KEY
import org.waste.of.time.WorldTools.config
import org.waste.of.time.storage.Cacheable

data class EntityCacheable(
    val entity: Entity
) : Cacheable {
    fun compound() = NbtCompound().apply {
        // Ensure the entity type ID is set
        EntityType.getId(entity.type)?.let { putString(Entity.ID_KEY, it.toString()) }

        // Save entity position
        put("Pos", NbtList().apply {
            add(NbtDouble.of(entity.x))
            add(NbtDouble.of(entity.y))
            add(NbtDouble.of(entity.z))
        })

        // Save entity rotation
        put("Rotation", NbtList().apply {
            add(NbtFloat.of(entity.yaw))
            add(NbtFloat.of(entity.pitch))
        })

        // Save entity motion
        val velocity = entity.velocity
        put("Motion", NbtList().apply {
            add(NbtDouble.of(velocity.x))
            add(NbtDouble.of(velocity.y))
            add(NbtDouble.of(velocity.z))
        })

        // Save UUID as most/least significant bits
        val uuid = entity.uuid
        putLong("UUIDMost", uuid.mostSignificantBits)
        putLong("UUIDLeast", uuid.leastSignificantBits)

        // Save air and fire ticks
        putShort("Air", entity.air.toShort())
        putShort("Fire", entity.fireTicks.toShort())

        // Save if entity is on ground
        putBoolean("OnGround", entity.isOnGround)

        if (config.entity.behavior.modifyEntityBehavior) {
            putByte("NoAI", config.entity.behavior.noAI.toByte())
            putByte("NoGravity", config.entity.behavior.noGravity.toByte())
            putByte("Invulnerable", config.entity.behavior.invulnerable.toByte())
            putByte("Silent", config.entity.behavior.silent.toByte())
        }

        if (config.entity.metadata.captureTimestamp) {
            putLong(TIMESTAMP_KEY, System.currentTimeMillis())
        }
    }

    override fun cache() {
        HotCache.entities.computeIfAbsent(entity.chunkPos) { mutableSetOf() }.apply {
            // Remove the entity if it already exists to update it
            removeIf { it.entity.uuid == entity.uuid }
            add(this@EntityCacheable)
        }
    }

    override fun flush() {
        val chunkPos = entity.chunkPos
        HotCache.entities[chunkPos]?.let { list ->
            list.remove(this)
            if (list.isEmpty()) {
                HotCache.entities.remove(chunkPos)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is EntityCacheable) return super.equals(other)
        return entity.uuid == other.entity.uuid
    }

    override fun hashCode() = entity.uuid.hashCode()
}
