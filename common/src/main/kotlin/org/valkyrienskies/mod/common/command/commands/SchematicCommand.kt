package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.mod.common.command.arguments.ShipArgument
import org.valkyrienskies.mod.common.schematic.VdexWrapper

object SchematicCommand {

    val shipsNeedingAttachmentLoad = Long2ObjectOpenHashMap<ByteArray>()

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(literal("schematic")
            .requires { it.hasPermission(2) }
            .then(literal("save")
                .then(argument("ship", ShipArgument.ships())
                    .then(argument("filename", StringArgumentType.word())
                        .executes { ctx ->
                            VdexWrapper.saveShip(ctx, ShipArgument.getShip(ctx, "ship") as ServerShip,
                                StringArgumentType.getString(ctx, "filename"))
                        }
                    )
                )
            ).then(literal("load")
                .then(argument("filename", StringArgumentType.word())
                    .executes { ctx ->
                        VdexWrapper.loadShip(ctx, StringArgumentType.getString(ctx, "filename"))
                    }))
        )
    }
}
