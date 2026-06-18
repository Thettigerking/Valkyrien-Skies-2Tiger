val minecraft_version: String by rootProject
val mixinextras_version: String by rootProject
val fabric_loader_version: String by rootProject
val fcap_version: String by rootProject
val vs_core_version: String by rootProject
val archives_base_name: String by rootProject

val sodium_version: String by project
val iris_version: String by project
val cc_tweaked_version: String by project
val dynmap_version: String by project
val hexcasting_version: String by project
val hextweaks_version: String by project
val ephemera_version: String by project
val hexal_version: String by project
val create_fabric_version: String by project
val fabric_api_version: String by project
val create_utilities_version: String by project
val energy_version: String by project
val immptl_version: String by project
val kotlin_fabric_version: String by project
val modmenu_version: String by project
val port_lib_modules: String by project
val port_lib_version: String by project
val config_api_id: String by project
val reach_entity_attributes_version: String by project
val fake_player_api_version: String by project
val milk_lib_version: String by project

plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("org.jetbrains.kotlin.jvm")
    id("com.matthewprenger.cursegradle")
    id("com.modrinth.minotaur")
}

apply(from = "../gradle-scripts/publish-curseforge.gradle")

architectury {
    platformSetupLoomIde()
    fabric()
}

configurations {
    create("common")
    create("shadowCommon") // Don't use shadow from the shadow plugin because we don't want IDEA to index this
    named("compileClasspath") {
        extendsFrom(configurations["common"])
    }
    named("runtimeClasspath") {
        extendsFrom(configurations["common"])
    }
    named("developmentFabric") {
        extendsFrom(configurations["common"])
    }
}

loom {
    accessWidenerPath = project(":common").loom.accessWidenerPath
}

dependencies {
    include(implementation(annotationProcessor("io.github.llamalad7:mixinextras-fabric:${mixinextras_version}")!!)!!)

    modImplementation("net.fabricmc:fabric-loader:${fabric_loader_version}")

    add("common", project(":common", configuration = "namedElements")) {
        isTransitive = false
    }
    add("shadowCommon", project(":common", configuration = "transformProductionFabric")) {
        isTransitive = false
    }

    // Depend on the fabric kotlin mod
    include(modImplementation("net.fabricmc:fabric-language-kotlin:${kotlin_fabric_version}")!!)

    include(modImplementation("fuzs.forgeconfigapiport:forgeconfigapiport-fabric:${fcap_version}")!!)
    modRuntimeOnly("maven.modrinth:forge-config-screens:v8.0.2-1.20.1-Fabric")


    modCompileOnly("maven.modrinth:create-utilities:${create_utilities_version}")
    modCompileOnly("maven.modrinth:sodium:${sodium_version}")
    // Disable indium until we update sodium to newer versions
    //modRuntimeOnly("maven.modrinth:indium:${indium_version}")
    modCompileOnly("maven.modrinth:iris:${iris_version}")

    modRuntimeOnly("maven.modrinth:modmenu:${modmenu_version}")


    // Depend on the fabric API
    modImplementation("net.fabricmc.fabric-api:fabric-api:${fabric_api_version}")

    modCompileOnly("teamreborn:energy:${energy_version}") { isTransitive = false }

    implementation("org.valkyrienskies.core:internal:${vs_core_version}")
    implementation("org.valkyrienskies.core:util:${vs_core_version}")
    runtimeOnly("org.valkyrienskies.core:impl:${vs_core_version}") {
        exclude( module = "netty-buffer")
        exclude( module = "fastutil")
        exclude( module = "kotlin-stdlib-jdk8")
    }
    // Shade vs-core
    add("shadowCommon", "org.valkyrienskies.core:impl:$vs_core_version") {
        exclude(module = "netty-buffer")
        exclude(module = "fastutil")
        exclude(module = "kotlin-stdlib-jdk8") // Don't shade kotlin-stdlib-jdk8, even though vs-core depends on it
        exclude(group = "com.google.guava")
        exclude(module = "jsonschema.module.addon")
    }

    include(implementation("com.fasterxml:classmate:1.5.1")!!)

    // CC Tweaked
    //modRuntimeOnly("cc.tweaked:cc-tweaked-${minecraft_version}-fabric:${cc_tweaked_version}")
    // CC Restitched
    modCompileOnly("maven.modrinth:cc-tweaked:${cc_tweaked_version}-fabric")

    //Very many players
    //modImplementation("curse.maven:vmp-fabric-552542:4754074")

    // EMF compat
    //todo: fix
    modCompileOnly("maven.modrinth:entity-model-features:3.0.7-fabric-1.20.1")
    modCompileOnly("maven.modrinth:entitytexturefeatures:7.0.6-fabric-1.20.1")

    modCompileOnly("curse.maven:vista-1368607:7929284")

    // Create compat
    //modImplementation("com.simibubi.create:create-fabric:${create_fabric_version}") {
    //    exclude group: "com.github.AlphaMode", module: "fakeconfigtoml"
    //}
    modCompileOnly("com.simibubi.create:create-fabric:$create_fabric_version")
    modRuntimeOnly("com.simibubi.create:create-fabric:$create_fabric_version")
    //modImplementation("com.tterrag.registrate_fabric:Registrate:${registrate_version}")

    //modImplementation("io.github.fabricators_of_create.Porting-Lib:Porting-Lib:$port_lib_version")
    port_lib_modules.split(",").forEach { module ->
        modCompileOnly("io.github.fabricators_of_create.Porting-Lib:$module:$port_lib_version")
    }
    modCompileOnly("curse.maven:vanillin-965702:6446557")

    modCompileOnly("curse.maven:forge-config-api-port-fabric-547434:$config_api_id")
    modCompileOnly("com.jamieswhiteshirt:reach-entity-attributes:${reach_entity_attributes_version}")
    modCompileOnly("dev.cafeteria:fake-player-api:${fake_player_api_version}")
    modCompileOnly("io.github.tropheusj:milk-lib:${milk_lib_version}")

    // Dynmap
    modCompileOnly("maven.modrinth:dynmap:${dynmap_version}")

    // Hexcasting
    modCompileOnly("at.petra-k.hexcasting:hexcasting-fabric-${minecraft_version}:${hexcasting_version}") { isTransitive = false }

    // HexTweaks
    modCompileOnly("maven.modrinth:hextweaks:$hextweaks_version")

    // Hexical
    // fixme modCompileOnly("miyucomics.hexical:hexical:$hexical_version") { isTransitive = false }

    // Ephemera
    modCompileOnly("maven.modrinth:ephemera:$ephemera_version")

    // Hexal
    modCompileOnly("ram.talia.hexal:hexal-fabric-$minecraft_version:$hexal_version") { isTransitive = false }

    modCompileOnly("com.github.iPortalTeam.ImmersivePortalsMod:imm_ptl_core:${immptl_version}") { isTransitive = false }
    modCompileOnly("com.github.iPortalTeam.ImmersivePortalsMod:q_misc_util:${immptl_version}") { isTransitive = false }
    modCompileOnly("com.github.iPortalTeam.ImmersivePortalsMod:build:${immptl_version}") { isTransitive = false }

    // Connectible Chains [Fabric]
    modCompileOnly("curse.maven:connectiblechains-415681:7148381")
}

// Copy the VS common access widener to the generated resources folder
//
// Note: We have to do this because fabric can"t find the access widener unless its in the fabric project
val generatedResourcesDir = file("src/generated/resources")
tasks.register<Copy>("copyAccessWidener") {
    from(project(":common").file("src/main/resources/valkyrienskies-common.accesswidener"))
    into(generatedResourcesDir)
}

// Add [generatedResourcesDir] as a folder to search for resources
sourceSets {
    main {
        resources {
            srcDir(generatedResourcesDir)
        }
    }
}

tasks.processResources {
    dependsOn("copyAccessWidener")
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version))
    }
}

tasks.shadowJar {
    configurations = listOf(project.configurations.getByName("shadowCommon"))
    archiveClassifier.set("dev-shadow")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE // Ignore duplicate valkyrienskies-common.accesswidener files
    dependencies {
        exclude(dependency("org.jetbrains.kotlin:.*:.*")) // Don"t shade kotlin!
        exclude(dependency("org.apache.commons:commons-lang3:.*")) // Don"t apache-commons, see #617
    }
    // Exclude dummy Optifine classes
    exclude("net/optifine/**")
}

tasks.remapJar {
    dependsOn(tasks.shadowJar)

    input.set(tasks.shadowJar.flatMap { it.archiveFile })

    archiveClassifier.set(null as String?)

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE // Ignore duplicate valkyrienskies-common.accesswidener files
}

tasks.jar {
    archiveClassifier.set("dev")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE // Ignore duplicate valkyrienskies-common.accesswidener files
}

tasks.sourcesJar {
    dependsOn("copyAccessWidener")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE // Ignore duplicate valkyrienskies-common.accesswidener files
    val commonSources = project(":common").tasks.named<Jar>("sourcesJar")
    dependsOn(commonSources)
    from(commonSources.flatMap { it.archiveFile }.map { zipTree(it) })
}

components.named<AdhocComponentWithVariants>("java") {
    withVariantsFromConfiguration(configurations.getByName("shadowRuntimeElements")) {
        skip()
    }
}

// Publish to Mavens
publishing {
    publications {
        create<MavenPublication>("mavenFabric") {
            groupId = "org.valkyrienskies"
            version = project.version.toString()
            artifactId = archives_base_name + "-" + project.name
            // Publish the dev shadow jar to maven
            artifact(tasks.shadowJar.flatMap { it.archiveFile }) {
                classifier = "dev-shadow"
            }
            from(components["java"])
        }
    }
}
