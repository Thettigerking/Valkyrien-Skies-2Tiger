package org.valkyrienskies.mod.common.command.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import net.minecraft.world.level.storage.LevelResource
import org.joml.Quaterniond
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.Vector3i
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.internal.joints.VSD6Joint
import org.valkyrienskies.core.internal.joints.VSDistanceJoint
import org.valkyrienskies.core.internal.joints.VSFixedJoint
import org.valkyrienskies.core.internal.joints.VSGearJoint
import org.valkyrienskies.core.internal.joints.VSJoint
import org.valkyrienskies.core.internal.joints.VSJointMaxForceTorque
import org.valkyrienskies.core.internal.joints.VSJointPose
import org.valkyrienskies.core.internal.joints.VSJointType
import org.valkyrienskies.core.internal.joints.VSPrismaticJoint
import org.valkyrienskies.core.internal.joints.VSRackAndPinionJoint
import org.valkyrienskies.core.internal.joints.VSRevoluteJoint
import org.valkyrienskies.core.internal.joints.VSSphericalJoint
import org.valkyrienskies.core.internal.joints.VSSpringJoint
import org.valkyrienskies.mod.api.toJOML
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.assembly.ShipAssembler
import org.valkyrienskies.mod.common.command.arguments.ShipArgument
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.safeRenameTo
import org.valkyrienskies.mod.common.schematic.VdexConstraintMetadata
import org.valkyrienskies.mod.common.schematic.VdexData
import org.valkyrienskies.mod.common.schematic.VdexIO
import org.valkyrienskies.mod.common.schematic.VdexMetadata
import org.valkyrienskies.mod.common.schematic.VdexShipEntry
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.floorToInt
import org.valkyrienskies.mod.common.yRange
import org.valkyrienskies.mod.util.StructureTemplateFillFromVoxelSet
import java.io.FileNotFoundException
import java.lang.Exception
import java.nio.file.Files
import java.nio.file.InvalidPathException

object SchematicCommand {

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(literal("schematic")
            .requires { it.hasPermission(2) }
            .then(literal("save")
                .then(argument("ship", ShipArgument.ships())
                    .then(argument("filename", StringArgumentType.word())
                        .executes { ctx ->
                            saveShip(ctx, ShipArgument.getShip(ctx, "ship") as ServerShip,
                                StringArgumentType.getString(ctx, "filename"))
                        }
                    )
                )
            ).then(literal("load")
                .then(argument("filename", StringArgumentType.word())
                    .executes { ctx ->
                        loadShip(ctx, StringArgumentType.getString(ctx, "filename"))
                    }))
        )
    }

    private fun loadShip(ctx: CommandContext<CommandSourceStack>, filename: String): Int {
        val level = ctx.source.level

        val worldDir = level.server.getWorldPath(LevelResource.ROOT)
        val schematicsDir = worldDir.resolve("schematics")
        Files.createDirectories(schematicsDir)

        var schem: VdexData? = null
        try {
            val filePath = schematicsDir.resolve("$filename.vdex")
            schem = VdexIO.read(filePath)
        } catch (e: FileNotFoundException) {
            ctx.source.sendFailure(
                Component.literal("No such file: $filename.vdex")
            )
            return 0
        } catch (e: InvalidPathException) {
            ctx.source.sendFailure(
                    Component.literal("Invalid path: $filename.vdex")
            )
            return 0
        } catch (e: IllegalStateException) {
            ctx.source.sendFailure(
                Component.literal("Not a valid VDex file: $filename.vdex")
            )
            return 0
        }

        // Vector3dc because we don't want to mutate this as we go along
        val position: Vector3dc = ctx.source.position.toJOML()

        val indexToShip = mutableMapOf<Int, ServerShip>()
        schem.metadata.ships.forEachIndexed { index, vdexShipEntry ->

            val ship = level.shipObjectWorld.createNewShipAtBlock(
                Vector3d(position).add(
                    vdexShipEntry.relativeX,
                    vdexShipEntry.relativeY,
                    vdexShipEntry.relativeZ
                ).floorToInt(),
                false,
                vdexShipEntry.scale,
                level.dimensionId
            )
            indexToShip[index] = ship

            ship.isStatic = vdexShipEntry.isStatic
            ship.safeRenameTo(level, vdexShipEntry.name)

            val template = StructureTemplate()
            template.load(level.holderLookup(Registries.BLOCK), schem.nbtData[vdexShipEntry.nbtFile]!!)

            // Place blocks in the ship's chunk claim
            val toCenter = ship.chunkClaim.getCenterBlockCoordinates(level.yRange, Vector3i())

            // Calculate corner position so the structure is centered in the claim
            val halfSize = Vector3d(template.size.x / 2.0, template.size.y / 2.0, template.size.z / 2.0)
            val cornerOfShip = BlockPos(
                toCenter.x - halfSize.x.toInt(),
                toCenter.y - halfSize.y.toInt(),
                toCenter.z - halfSize.z.toInt()
            )

            val settings = StructurePlaceSettings()
            settings.rotationPivot = cornerOfShip
            template.placeInWorld(level, cornerOfShip, cornerOfShip, settings, level.random, Block.UPDATE_CLIENTS)
        }

        val gtpa = ValkyrienSkiesMod.getOrCreateGTPA(level.dimensionId)
        schem.metadata.constraints.forEach { pair ->
            val joint = pair.first
            val meta = pair.second

            val ship0 = indexToShip[meta.shipIndex0] ?: return@forEach
            val ship1 = indexToShip[meta.shipIndex1] ?: return@forEach

            val newPos0 = ship0.shipAABB?.center(Vector3d())?.add(meta.position0offset, Vector3d()) ?: return@forEach
            val newRot0 = joint.pose0.rot

            val newPos1 = ship1.shipAABB?.center(Vector3d())?.add(meta.position1offset, Vector3d()) ?: return@forEach
            val newRot1 = joint.pose1.rot

            val newId0 = ship0.id
            val newId1 = ship1.id

            // Absolutely hideous
            val newJoint = when (joint) {
                is VSFixedJoint -> joint.copy(shipId0 = newId0, shipId1 = newId1, pose0 = VSJointPose(newPos0, newRot0), pose1 = VSJointPose(newPos1, newRot1))
                is VSDistanceJoint -> joint.copy(shipId0 = newId0, shipId1 = newId1, pose0 = VSJointPose(newPos0, newRot0), pose1 = VSJointPose(newPos1, newRot1))
                is VSSpringJoint -> joint.copy(shipId0 = newId0, shipId1 = newId1, pose0 = VSJointPose(newPos0, newRot0), pose1 = VSJointPose(newPos1, newRot1))
                is VSPrismaticJoint -> joint.copy(shipId0 = newId0, shipId1 = newId1, pose0 = VSJointPose(newPos0, newRot0), pose1 = VSJointPose(newPos1, newRot1))
                is VSSphericalJoint -> joint.copy(shipId0 = newId0, shipId1 = newId1, pose0 = VSJointPose(newPos0, newRot0), pose1 = VSJointPose(newPos1, newRot1))
                is VSRevoluteJoint -> joint.copy(shipId0 = newId0, shipId1 = newId1, pose0 = VSJointPose(newPos0, newRot0), pose1 = VSJointPose(newPos1, newRot1))
                is VSGearJoint -> joint.copy(shipId0 = newId0, shipId1 = newId1, pose0 = VSJointPose(newPos0, newRot0), pose1 = VSJointPose(newPos1, newRot1))
                is VSRackAndPinionJoint -> joint.copy(shipId0 = newId0, shipId1 = newId1, pose0 = VSJointPose(newPos0, newRot0), pose1 = VSJointPose(newPos1, newRot1))
                is VSD6Joint -> joint.copy(shipId0 = newId0, shipId1 = newId1, pose0 = VSJointPose(newPos0, newRot0), pose1 = VSJointPose(newPos1, newRot1))
                else -> joint
            }

            gtpa.addJoint(newJoint, 2) {}
        }

        return 1
    }

    private fun saveShip(ctx: CommandContext<CommandSourceStack>, mainShip: ServerShip, filename: String): Int {
        val level = ctx.source.level

        // Find all connected ships via constraints
        val gtpa = ValkyrienSkiesMod.getOrCreateGTPA(level.dimensionId)
        val connectedIds = gtpa.getAllConnectedShips(mainShip.id)
        val allShips = connectedIds.mapNotNull { id ->
            level.shipObjectWorld.allShips.getById(id)
        }

        // Main ship is index 0; find its index in the connected set
        val mainShipIndex = allShips.indexOfFirst { it.id == mainShip.id }.coerceAtLeast(0)

        // Reorder so main ship is first
        val orderedShips = mutableListOf<ServerShip>()
        orderedShips.add(allShips[mainShipIndex])
        for (i in allShips.indices) {
            if (i != mainShipIndex) orderedShips.add(allShips[i])
        }

        val mainPos = orderedShips[0].transform.position
        val shipEntries = mutableListOf<VdexShipEntry>()
        val nbtData = mutableMapOf<String, CompoundTag>()

        for (idx in orderedShips.indices) {
            val ship = orderedShips[idx]
            val nbtFileName = "ship_$idx.nbt"
            val blocks = collectShipBlocks(level, ship)
            if (blocks.isEmpty()) continue

            val minMax = ShipAssembler.findMinAndMax(blocks)
            val minB = minMax.first
            val maxB = minMax.second
            val template = StructureTemplate()
            (template as StructureTemplateFillFromVoxelSet).`vs$fillFromVoxelSet`(
                level, blocks, listOf(ship),
                ShipAssembler.SingleItemMap(ship.id, Vector3d(), Vector3d()),
                minB, maxB
            )

            val tag = template.save(CompoundTag())
            nbtData[nbtFileName] = tag

            val relPos = Vector3d(ship.transform.position).sub(mainPos)
            shipEntries.add(VdexShipEntry(
                name = ship.slug ?: "ship_$idx",
                nbtFile = nbtFileName,
                relativeX = relPos.x,
                relativeY = relPos.y,
                relativeZ = relPos.z,
                isStatic = ship.isStatic,
                scale = ship.transform.scaling.x()
            ))
        }

        // Build ship ID -> index map for constraint references
        val shipIdToIndex = mutableMapOf<Long, Int>()
        for (idx in orderedShips.indices) {
            shipIdToIndex[orderedShips[idx].id] = idx
        }

        // Collect constraints between ships in the set
        val constraintEntries = mutableListOf<Pair<VSJoint, VdexConstraintMetadata>>()
        val seenJoints = mutableSetOf<Any>()
        for (ship in orderedShips) {
            val jointIds = gtpa.getJointsFromShip(ship.id) ?: continue
            for (jointId in jointIds) {
                if (!seenJoints.add(jointId)) continue
                val joint = gtpa.getJointById(jointId) ?: continue
                val idx0 = shipIdToIndex[joint.shipId0] ?: continue
                val idx1 = shipIdToIndex[joint.shipId1] ?: continue

                // We can't rely on our ship iterator here because it could be either ship0 OR ship1
                val firstShip = level.shipObjectWorld.allShips.getById(joint.shipId0!!)!!
                val secondShip = level.shipObjectWorld.allShips.getById(joint.shipId1!!)!!

                constraintEntries.add(Pair(
                    joint,
                    VdexConstraintMetadata(
                        idx0,
                        idx1,
                        joint.pose0.pos.sub(firstShip.shipAABB!!.center(Vector3d()), Vector3d()),
                        joint.pose1.pos.sub(secondShip.shipAABB!!.center(Vector3d()), Vector3d()),
                    )
                ))/*VdexConstraintEntry(
                    type = joint.javaClass.simpleName,
                    shipIndex0 = idx0,
                    shipIndex1 = idx1,
                    pose0 = VdexJointPose.fromPosRot(
                        Vector3d(joint.pose0.pos), Quaterniond(joint.pose0.rot)
                    ),
                    pose1 = VdexJointPose.fromPosRot(
                        Vector3d(joint.pose1.pos), Quaterniond(joint.pose1.rot)
                    )
                ))*/
            }
        }

        val metadata = VdexMetadata(
            version = 1,
            mainShipIndex = 0,
            ships = shipEntries,
            constraints = constraintEntries,
            shipIdToIndex = shipIdToIndex
        )

        // Save to world/schematics/ directory
        val worldDir = level.server.getWorldPath(LevelResource.ROOT)
        val schematicsDir = worldDir.resolve("schematics")
        Files.createDirectories(schematicsDir)
        val filePath = schematicsDir.resolve("$filename.vdex")

        VdexIO.write(filePath, metadata, nbtData)

        ctx.source.sendSuccess({
            Component.literal("Saved ${orderedShips.size} ship(s) to $filename.vdex (${constraintEntries.size} constraints)")
        }, true)
        return 1
    }

    private fun collectShipBlocks(level: ServerLevel, ship: ServerShip): List<BlockPos> {
        val blocks = mutableListOf<BlockPos>()
        //if (ship !is LoadedServerShip) return blocks

        ship.activeChunksSet.forEach { cx, cz ->
            val chunk = level.getChunk(cx, cz)
            val sections = chunk.sections
            for (sIdx in sections.indices) {
                val section = sections[sIdx] ?: continue
                if (section.hasOnlyAir()) continue
                val baseY = chunk.getSectionYFromSectionIndex(sIdx) shl 4
                for (lx in 0..15) {
                    for (ly in 0..15) {
                        for (lz in 0..15) {
                            val state = section.getBlockState(lx, ly, lz)
                            if (!state.isAir) {
                                blocks.add(BlockPos(
                                    (cx shl 4) + lx,
                                    baseY + ly,
                                    (cz shl 4) + lz
                                ))
                            }
                        }
                    }
                }
            }
        }
        return blocks
    }
}
