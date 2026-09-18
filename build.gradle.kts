plugins {
    id("fabric-loom") version "1.10.5"
    kotlin("jvm") version "2.1.20"
}

version = property("mod_version") as String
group = property("maven_group") as String

base {
    archivesName.set(property("archives_base_name") as String)
}

repositories {
    // Loom ja adiciona a maven da Mojang e da Fabric, mas deixo explicito.
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    maven("https://maven.impactdev.net/repository/development/") { name = "ImpactDev (Cobblemon)" }
    maven("https://maven.nucleoid.xyz/") { name = "Nucleoid (sgui)" }
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    // Mojmap: os nomes batem 1:1 com o codigo-fonte do Cobblemon (ServerPlayer, Component, ...)
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")

    modImplementation("com.cobblemon:fabric:${property("cobblemon_version")}")

    // sgui vai embutido no jar (jar-in-jar), entao o servidor nao precisa baixar a parte
    modImplementation("eu.pb4:sgui:${property("sgui_version")}")
    include("eu.pb4:sgui:${property("sgui_version")}")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version.toString()))
    }
}

java {
    withSourcesJar()
}

// Configura o toolchain do Java e do Kotlin de uma vez so.
kotlin {
    jvmToolchain(21)
}

tasks.jar {
    from("LICENSE") { rename { "${it}_${base.archivesName.get()}" } }
}
