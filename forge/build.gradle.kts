val minecraft_version: String by rootProject
val mixinextras_version: String by rootProject
val fabric_loader_version: String by rootProject
val fcap_version: String by rootProject
val vs_core_version: String by rootProject
val enabled_platforms: String by rootProject
val archives_base_name: String by rootProject

val sodium_version: String by project
val iris_version: String by project
val alexscaves_version: String by project
val tis3d_version: String by project
val cc_tweaked_version: String by project
val dynmap_version: String by project
val hexcasting_version: String by project
val hextweaks_version: String by project
val ephemera_version: String by project
val hexal_version: String by project
val moonlight_version: String by project
val create_fabric_version: String by project
val fabric_api_version: String by project
val create_utilities_version: String by project
val energy_version: String by project
val immptl_version: String by project
val createbigcannons_version: String by project
val createbigcannons_build: String by project
val rpl_version: String by project
val forge_version: String by project
val embeddium_version: String by project
val oculus_version: String by project
val twilightforest_version: String by project
val create_version: String by project
val ponder_version: String by project
val flywheel_version: String by project
val registrate_version: String by project
val oc2r_version: String by project
val mekanism_version: String by project
val spark_version: String by project
val kotlin_version: String by project
val krunch_version: String by project
val krunch_api_version: String by project

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // Make sure this version matches the one included in Kotlin for Forge
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
        // OPTIONAL Gradle plugin for Kotlin Serialization
        classpath("org.jetbrains.kotlin:kotlin-serialization:1.9.22")
    }
}

plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("org.jetbrains.kotlin.jvm")
    id("com.matthewprenger.cursegradle")
    id("com.modrinth.minotaur")
}

apply(from = "../gradle-scripts/publish-curseforge.gradle")

architectury {
    platformSetupLoomIde()
    forge()
}

sourceSets {
    main {
        resources {
            srcDir("$buildDir/generated/resources")
        }
    }
}

loom {
    accessWidenerPath = project(":common").loom.accessWidenerPath

    forge {
        mixinConfig("valkyrienskies-common.mixins.json")
        mixinConfig("valkyrienskies-forge.mixins.json")
        convertAccessWideners.set(true)
        extraAccessWideners.add(loom.accessWidenerPath.get().asFile.name)
    }
    mixin {
        defaultRefmapName = "valkyrienskies-refmap.json"
    }
}

configurations {
    create("common")
    create("shadowCommon") // Don't use shadow from the shadow plugin because we don't want IDEA to index this
    create("core")
    named("compileClasspath") {
        extendsFrom(configurations["common"])
    }
    named("runtimeClasspath") {
        extendsFrom(configurations["common"])
    }
    named("developmentForge") {
        extendsFrom(configurations["common"])
    }
    named("compileClasspath") {
        extendsFrom(configurations["core"])
    }
    named("forgeRuntimeLibrary") {
        extendsFrom(configurations["core"])
    }
    named("shadowCommon") {
        extendsFrom(configurations["core"])
    }
}

dependencies {
    implementation(annotationProcessor("io.github.llamalad7:mixinextras-common:${mixinextras_version}")!!)
    implementation(include("io.github.llamalad7:mixinextras-forge:${mixinextras_version}")!!)
    annotationProcessor("net.fabricmc:sponge-mixin:0.12.5+mixin.0.8.5") // use fabric mixin so we can write interface injectors (conditionally loaded if mixinbooster is enabled)

    forge("net.minecraftforge:forge:${forge_version}")

    add("common", project(":common", configuration = "namedElements")) {
        isTransitive = false
    }
    add("shadowCommon", project(":common", configuration = "transformProductionForge")) {
        isTransitive = false
    }

    modCompileOnly("maven.modrinth:embeddium:${embeddium_version}")
    //modRuntimeOnly("maven.modrinth:embeddium:${embeddium_version}")
    modCompileOnly("maven.modrinth:oculus:${oculus_version}")

    modRuntimeOnly("maven.modrinth:forge-config-screens:v8.0.2-1.20.1-Forge")

    // Twilight Forest
    modCompileOnly("teamtwilight:twilightforest:${twilightforest_version}:universal")
    //modRuntimeOnly("teamtwilight:twilightforest:${twilightforest_version}:universal")

    // CBC
    //modRuntimeOnly("com.rbasamoyai:createbigcannons:${createbigcannons_version}+mc.${minecraft_version}-forge-build.250") { isTransitive = false }
    //modRuntimeOnly("com.rbasamoyai:ritchiesprojectilelib:${rpl_version}+mc.${minecraft_version}-forge")


    // Create compat
    modCompileOnly("com.simibubi.create:create-${minecraft_version}:${create_version}:slim") { isTransitive = false }
    modRuntimeOnly("com.simibubi.create:create-${minecraft_version}:${create_version}:slim") { isTransitive = false }
    modCompileOnly("net.createmod.ponder:Ponder-Forge-${minecraft_version}:${ponder_version}")
    modRuntimeOnly("net.createmod.ponder:Ponder-Forge-${minecraft_version}:${ponder_version}")
    modCompileOnly("curse.maven:vanillin-965702:6446560")
    modRuntimeOnly("curse.maven:vanillin-965702:6446560")
    modCompileOnly("dev.engine-room.flywheel:flywheel-forge-api-${minecraft_version}:${flywheel_version}")
    modRuntimeOnly("dev.engine-room.flywheel:flywheel-forge-${minecraft_version}:${flywheel_version}")
    modCompileOnly("com.tterrag.registrate:Registrate:${registrate_version}")
    modRuntimeOnly("com.tterrag.registrate:Registrate:${registrate_version}")

    // Weather2 1.20.1
    //modRuntimeOnly("curse.maven:weather-storms-tornadoes-237746:5244118")
    //modRuntimeOnly("curse.maven:coroutil-237749:5096038")

    // CC Tweaked
    //modRuntimeOnly("cc.tweaked:cc-tweaked-${minecraft_version}-forge:${cc_tweaked_version}")

    // OpenComputers 2: Reimagined
    modCompileOnly("maven.modrinth:oc2r:${oc2r_version}")

    // EMF compat
    //todo: fix
    modCompileOnly("maven.modrinth:entity-model-features:3.0.7-forge-1.20.1")
    modCompileOnly("maven.modrinth:entitytexturefeatures:7.0.6-forge-1.20.1")

    modCompileOnly("curse.maven:vista-1368607:7929283")

    modCompileOnly("maven.modrinth:create-utilities:0.2.0+1.20.1")
    modCompileOnly("teamreborn:energy:${energy_version}") {
        isTransitive = false
    }
    // TIS-3d
    modCompileOnly("maven.modrinth:tis3d:${tis3d_version}")

    // Modular Routers
    modCompileOnly("curse.maven:mr-250294:4696089")

    // Modular Force Field System
    modCompileOnly("maven.modrinth:mffs:5.1.18")

    // Epic Fight
    modCompileOnly("maven.modrinth:epic-fight:20.10.3")

    // Dynmap
    modCompileOnly("maven.modrinth:dynmap:${dynmap_version}")

    // Hexcasting
    modCompileOnly("at.petra-k.hexcasting:hexcasting-forge-${minecraft_version}:${hexcasting_version}") { isTransitive = false }

    // HexTweaks
    modCompileOnly("maven.modrinth:hextweaks:$hextweaks_version")

    // Ephemera
    modCompileOnly("maven.modrinth:ephemera:$ephemera_version")

    // Hexal
    modCompileOnly("ram.talia.hexal:hexal-forge-$minecraft_version:$hexal_version") { isTransitive = false }

    // Integrated Dynamics
    modCompileOnly("curse.maven:integrated-dynamics-236307:5297722")
    modCompileOnly("curse.maven:cyclops-core-232758:5262063")
    modCompileOnly("curse.maven:common-capabilities-247007:4987207")

    // Very Many Players
    //modCompileOnly("maven.modrinth:vmp-forge:0.2.0+beta.7.101+1.20.1")

    // Mekanism
    modCompileOnly("maven.modrinth:mekanism:${mekanism_version}")
    //modRuntimeOnly("maven.modrinth:mekanism:${mekanism_version}")

    // Spark Profiler
    modRuntimeOnly ("maven.modrinth:spark:${spark_version}")

    // Connectible Chains [Forge]
    modCompileOnly("curse.maven:connectible-chains-forge-418514:6142294")
    //modRuntimeOnly("curse.maven:connectible-chains-forge-418514:6142294")


    // Add Kotlin for Forge (3.12.0)
    forgeRuntimeLibrary("maven.modrinth:kotlin-for-forge:${kotlin_version}")

    // Shade vs-core
    implementation("org.valkyrienskies.core:util:${vs_core_version}")
    implementation("org.valkyrienskies.core:internal:${vs_core_version}") {
        exclude(group = "org.joml", module = "joml")
    }
    runtimeOnly("org.valkyrienskies.core:impl:${vs_core_version}") {
        exclude(group = "org.joml", module = "joml")
    }

    forgeRuntimeLibrary(add("shadowCommon", "org.valkyrienskies.core:impl:$vs_core_version") {
        isTransitive = false
    })

    // region Manually include every single dependency of vs-core (total meme)
    forgeRuntimeLibrary(include("org.valkyrienskies.core:api:${vs_core_version}") {
        exclude(group = "org.joml", module = "joml")
        isTransitive = false
    }!!)

    forgeRuntimeLibrary(add("shadowCommon", "org.valkyrienskies.core:internal:$vs_core_version") {
        isTransitive = false
    })

    forgeRuntimeLibrary(add("shadowCommon", "org.valkyrienskies.core:util:$vs_core_version") {
        isTransitive = false
    })

    forgeRuntimeLibrary(add("shadowCommon", "org.valkyrienskies:physics_api_krunch:$krunch_version") {
        isTransitive = false
    })

    forgeRuntimeLibrary(add("shadowCommon", "org.valkyrienskies:physics_api:$krunch_api_version") {
        isTransitive = false
    })

    forgeRuntimeLibrary(include("javax.inject:javax.inject:1") { isTransitive = false }!!)

    // JOML for Math
    forgeRuntimeLibrary(include("org.joml:joml-primitives:1.10.0")!!)

    // Apache Commons Math for Linear Programming
    forgeRuntimeLibrary(include("org.apache.commons:commons-math3:3.6.1") { isTransitive = false }!!)

    // Jackson Binary Dataformat for Object Serialization
    val jacksonVersion = "2.14.0"
    // forked to remove module-info
    forgeRuntimeLibrary(include("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion-rubyfork") { isTransitive = false }!!)
    forgeRuntimeLibrary(include("com.fasterxml.jackson.module:jackson-module-parameter-names:$jacksonVersion") { isTransitive = false }!!)
    forgeRuntimeLibrary(include("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:$jacksonVersion") { isTransitive = false }!!)
    forgeRuntimeLibrary(include("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion") { isTransitive = false }!!)
    forgeRuntimeLibrary(include("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion") { isTransitive = false }!!)
    forgeRuntimeLibrary(include("com.fasterxml.jackson.core:jackson-core:$jacksonVersion") { isTransitive = false }!!)
    forgeRuntimeLibrary(include("com.github.Rubydesic:jackson-kotlin-dsl:1.2.0") { isTransitive = false }!!)
    forgeRuntimeLibrary(include("com.networknt:json-schema-validator:1.0.71") { isTransitive = false }!!)
    forgeRuntimeLibrary(include("com.ethlo.time:itu:1.7.0") { isTransitive = false }!!)
    forgeRuntimeLibrary(include("com.github.victools:jsonschema-module-jackson:4.25.0") { isTransitive = false }!!)
    forgeRuntimeLibrary(include("com.github.victools:jsonschema-generator:4.25.0") { isTransitive = false }!!)
    forgeRuntimeLibrary(include("com.fasterxml:classmate:1.5.1") { isTransitive = false }!!)
    forgeRuntimeLibrary(include("com.flipkart.zjsonpatch:zjsonpatch:0.4.11") { isTransitive = false }!!)
    forgeRuntimeLibrary(include("org.apache.commons:commons-collections4:4.3") { isTransitive = false }!!)
    forgeRuntimeLibrary(include("com.google.dagger:dagger:2.43.2") { isTransitive = false }!!)

    // endregion
}

// Copy the VS common access widener to the generated resources folder
val generatedResourcesDir = layout.buildDirectory.dir("generated/resources")

tasks.register<Copy>("copyAccessWidener") {
    from(project(":common").file("src/main/resources/valkyrienskies-common.accesswidener"))
    into(generatedResourcesDir)
}

tasks.processResources {
    dependsOn("copyAccessWidener")
    inputs.property("version", project.version)

    filesMatching("META-INF/mods.toml") {
        expand(mapOf("version" to project.version))
    }
}

tasks.shadowJar {
    configurations = listOf(project.configurations.getByName("shadowCommon"))
    archiveClassifier.set("dev-shadow")

    exclude("fabric.mod.json")
    exclude("architectury.common.json")
    // Exclude dummy Optifine classes
    exclude("net/optifine/**")
}

tasks.remapJar {
    dependsOn(tasks.shadowJar)

    input.set(tasks.shadowJar.flatMap { it.archiveFile })

    archiveClassifier.set(null as String?)
}

tasks.compileKotlin {
    kotlinOptions.jvmTarget = "17"
}

tasks.jar {
    archiveClassifier.set("dev")
}

tasks.sourcesJar {
    dependsOn("copyAccessWidener")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    val commonSources = project(":common").tasks.named<Jar>("sourcesJar")
    dependsOn(commonSources)
    from(commonSources.flatMap { it.archiveFile }.map { zipTree(it) })
}

components.named<AdhocComponentWithVariants>("java") {
    withVariantsFromConfiguration(configurations.getByName("shadowRuntimeElements")) {
        skip()
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenForge") {
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
