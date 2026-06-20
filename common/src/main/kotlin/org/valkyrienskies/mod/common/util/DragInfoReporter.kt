package org.valkyrienskies.mod.common.util

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.server.level.ServerLevel
import org.joml.Vector3dc
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.shipObjectWorld

object DragInfoReporter {
    val shipDragValues = Long2ObjectOpenHashMap<Vector3dc>()
    val shipLiftValues = Long2ObjectOpenHashMap<Vector3dc>()

    fun tick(level: ServerLevel) {
        // This data only feeds the client F3+B ship drag/lift debug renderer, so don't pay for
        // rebuilding these maps every tick unless explicitly enabled.
        if (!VSGameConfig.SERVER.Performance.computeDragDebugInfo) {
            if (!shipDragValues.isEmpty()) {
                shipDragValues.clear()
                shipLiftValues.clear()
            }
            return
        }
        if (level !== level.server.overworld()) {
            return
        }
        shipDragValues.clear()
        shipLiftValues.clear()
        level.shipObjectWorld.loadedShips.forEach { ship ->
            shipDragValues.put(ship.id, ship.dragController!!.getDragForce())
            shipLiftValues.put(ship.id, ship.dragController!!.getLiftForce())
        }
    }
}
