import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm").version(deps.kotlin)
}

group = "one.kuring"


repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${deps.kotlin_coroutines}")
    implementation("cn.danielw:fast-object-pool:${deps.object_pool}")
    implementation("com.conversantmedia:disruptor:${deps.object_pool_disruptor}")
    implementation("org.jctools:jctools-core:${deps.jctools}")
//    implementation("com.tdunning:t-digest:3.3")
    implementation(project(":kuring-natives"))
    testImplementation("org.junit.jupiter:junit-jupiter:${deps.junit}")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:${deps.kotlin_test}")
    testImplementation("org.jetbrains.kotlin:kotlin-test:${deps.kotlin_test}")
}
