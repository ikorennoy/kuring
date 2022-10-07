import dev.nokee.runtime.nativebase.OperatingSystemFamily

plugins {
    id("java")
    id("dev.nokee.jni-library")
    id("dev.nokee.c-language")
    id("maven-publish")
}

tasks.withType<dev.nokee.language.c.tasks.CCompile> {
    compilerArgs.add("-D_GNU_SOURCE")
}


fun getOs(osFamily: OperatingSystemFamily): String {
    return if (osFamily.isLinux) {
        "linux"
    } else if (osFamily.isWindows) {
        "windows"
    } else {
        throw IllegalStateException("unsupported OS $osFamily")
    }
}

fun getArch(arch: dev.nokee.runtime.nativebase.MachineArchitecture): String {
    return if (arch.is64Bit) {
        "x86_64"
    } else if (arch.is32Bit) {
        "x86"
    } else {
        throw IllegalStateException("unsupported arch $arch")
    }

}

library {
    targetMachines.set(listOf(machines.linux, machines.windows))

    variants.configureEach {
        val osName = when {
            targetMachine.operatingSystemFamily.isWindows -> "windows"
            targetMachine.operatingSystemFamily.isLinux -> "linux"
            else -> throw GradleException("Unknown operating system family")
        }
        val architectureName = when {
            targetMachine.architecture.is32Bit -> "x86"
            targetMachine.architecture.is64Bit -> "x86_64"
            else -> throw GradleException("Unknown architecture")
        }
        resourcePath.set("libs/${osName}-${architectureName}")
    }
}
