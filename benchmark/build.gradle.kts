plugins {
    kotlin("jvm").version(deps.kotlin)
    id("com.github.johnrengelman.shadow") version(deps.shadow_plugin)
}


repositories {
    mavenCentral()
}

dependencies {
    implementation("info.picocli:picocli:4.6.3")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}