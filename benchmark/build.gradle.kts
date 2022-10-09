plugins {
    kotlin("jvm").version(deps.kotlin)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(rootProject)
    implementation(project(":kuring-natives"))
    implementation("com.tdunning:t-digest:3.3")
    implementation("info.picocli:picocli:4.6.3")
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