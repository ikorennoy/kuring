import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm").version(deps.kotlin)
}

group = "one.kuring"


repositories {
    mavenCentral()
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${deps.kotlin_coroutines}")
    implementation("cn.danielw:fast-object-pool:${deps.object_pool}")
    implementation("com.conversantmedia:disruptor:${deps.object_pool_disruptor}")
    implementation("org.jctools:jctools-core:${deps.jctools}")
    testImplementation("org.junit.jupiter:junit-jupiter:${deps.junit}")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:${deps.kotlin_test}")
    testImplementation("org.jetbrains.kotlin:kotlin-test:${deps.kotlin_test}")
}

tasks.test {
    useJUnitPlatform()
}

// compile shared lib

val arch: String
    get() {
        val archString = System.getProperty("os.arch")
        return if ("x86_64".equals(archString, true) || "amd64".equals(archString, true)) {
            "amd64"
        } else if ("aarch64".equals(archString, true)) {
            "arm64"
        } else {
            throw IllegalArgumentException("Architecture $archString is not supported")
        }
    }

val jdkPath: File
    get() {
        val javaHome = System.getenv("JAVA_HOME") ?: "/usr/lib/jvm/java-8-openjdk-$arch"
        return File(javaHome)
    }

println("JAVA_HOME: $jdkPath")
println("ARCH: $arch")

val cWorkDir = project.file("src/main/c")
val objectsOutputDir = project.file("build/generated")
val sharedLib = project.file("build/libjasyncfio.so").absolutePath

// all targets
val syscallTarget = File(objectsOutputDir, "syscall.o")
val javaIoUringNativesTarget = File(objectsOutputDir, "java_io_uring_natives.o")
val ioUringConstantsTarget = File(objectsOutputDir, "io_uring_constants.o")
val fileIoConstantsTarget = File(objectsOutputDir, "file_io_constants.o")


task("fileIoConstants", Exec::class) {
    val fileIoConstantsSource = project.file("src/main/c/file_io_constants.c")
    workingDir = cWorkDir
    commandLine = getCompileObjectArgs(fileIoConstantsSource, fileIoConstantsTarget)
}

task("ioUringConstants", Exec::class) {
    val ioUringConstantsSrc = project.file("src/main/c/io_uring_constants.c")
    workingDir = cWorkDir
    commandLine = getCompileObjectArgs(ioUringConstantsSrc, ioUringConstantsTarget)
}

task("javaIoUringNatives", Exec::class) {
    val javaIoUringNativesSource = project.file("src/main/c/java_io_uring_natives.c")
    workingDir = cWorkDir
    commandLine = getCompileObjectArgs(javaIoUringNativesSource, javaIoUringNativesTarget)
}

task("syscall", Exec::class) {
    val syscallSource = project.file("src/main/c/syscall.c")
    workingDir = cWorkDir
    commandLine = getCompileObjectArgs(syscallSource, syscallTarget)
}

task("sharedLib", Exec::class) {
    dependsOn(
        tasks.getByName("fileIoConstants"),
        tasks.getByName("ioUringConstants"),
        tasks.getByName("javaIoUringNatives"),
        tasks.getByName("syscall")
    )
    commandLine = listOf(
        "gcc",
        "-shared",
        "-o",
        sharedLib,
        syscallTarget.absolutePath,
        javaIoUringNativesTarget.absolutePath,
        ioUringConstantsTarget.absolutePath,
        fileIoConstantsTarget.absolutePath
    )
}

fun getCompileObjectArgs(sourceFile: File, outputFile: File): List<String> {
    return listOf(
        "gcc",
        "-c",
        "-g",
        "-O2",
        "-D_GNU_SOURCE",
        "-fpic",
        "-Wall",
        "-Wcast-qual",
        "-Wshadow",
        "-Wformat=2",
        "-Wundef",
        "-Werror=float-equal",
        "-Werror=strict-prototypes",
        "-o",
        outputFile.absolutePath,
        "-I",
        "${jdkPath.absolutePath}/include",
        "-I",
        "${jdkPath.absolutePath}/include/linux",
        sourceFile.absolutePath
    )
}


tasks.withType<KotlinCompile> {
    dependsOn.add(tasks.getByName("sharedLib"))
    kotlinOptions.jvmTarget = "1.8"
}


