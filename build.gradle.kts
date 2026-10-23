plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "dev.exiled"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release = 21
    }

    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    test {
        useJUnitPlatform()
    }

    runServer {
        // Drop Multiverse-Core into run/plugins/ to test /mv create.
        minecraftVersion("1.21.11")
    }
}
