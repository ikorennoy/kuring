import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm").version(deps.kotlin)
    id("com.github.johnrengelman.shadow") version(deps.shadow_plugin)
}


repositories {
    mavenCentral()
}

dependencies {
    implementation(rootProject)
    implementation("info.picocli:picocli:4.6.3")
}

tasks.withType(ShadowJar::class) {
    mergeServiceFiles()
    minimize()
}

tasks.jar {
    manifest.attributes["Main-Class"] = "one.kuring.benchmark.BenchmarkKt"
    val dependencies = configurations
        .runtimeClasspath
        .get()
        .map(::zipTree) // OR .map { zipTree(it) }
    from(dependencies)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}