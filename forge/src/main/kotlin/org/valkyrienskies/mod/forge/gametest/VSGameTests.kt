package org.valkyrienskies.mod.forge.gametest

import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.MOD_ID

/**
 * Forge GameTest suite for Valkyrien Skies 2.
 *
 * Tests are registered via [RegisterGameTestsEvent] in [ValkyrienSkiesModForge].
 * Structure templates live in: data/valkyrienskies/structures/<name>.snbt
 *
 * Run with the "Forge Game Test Server" run configuration, or via:
 *   ./gradlew :forge:runGameTestServer
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class VSGameTests {

    /**
     * Sanity check: verify blocks can be placed and detected within a test structure.
     */
    @GameTest(template = "empty")
    fun testBlockPlacement(helper: GameTestHelper) {
        val pos = BlockPos(1, 2, 1)
        helper.setBlock(pos, Blocks.STONE)
        helper.assertBlockPresent(Blocks.STONE, pos)
        helper.succeed()
    }

    /**
     * Verify that the VS mod ID is registered correctly.
     */
    @GameTest(template = "empty")
    fun testModIdIsCorrect(helper: GameTestHelper) {
        if (MOD_ID != "valkyrienskies") {
            helper.fail("Expected mod ID 'valkyrienskies' but found '$MOD_ID'")
            return
        }
        helper.succeed()
    }

    /**
     * Verify that a block can be placed and then successfully removed (set to air).
     */
    @GameTest(template = "empty")
    fun testBlockRemoval(helper: GameTestHelper) {
        val pos = BlockPos(2, 2, 2)
        helper.setBlock(pos, Blocks.DIRT)
        helper.assertBlockPresent(Blocks.DIRT, pos)
        helper.setBlock(pos, Blocks.AIR)
        helper.assertBlockNotPresent(Blocks.DIRT, pos)
        helper.succeed()
    }
}
