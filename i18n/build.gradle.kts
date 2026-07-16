import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import yokai.build.generatedBuildDir
import java.io.File

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.moko)
}

kotlin {
    android {
        namespace = "yokai.i18n"
        compileSdk = AndroidConfig.COMPILE_SDK
        minSdk = AndroidConfig.MIN_SDK
        withHostTest {}
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(AndroidConfig.JavaVersion.toString()))
        }
        androidResources {
            enable = true
        }
    }
//    iosX64()
//    iosArm64()
//    iosSimulatorArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                api(libs.moko.resources)
                api(libs.moko.resources.compose)
            }
        }
        androidMain {
        }
//        iosMain {
//        }
    }
}

val generatedAndroidResourceDir = generatedBuildDir.resolve("android/res")

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addStaticSourceDirectory("src/commonMain/resources")
        variant.sources.res?.addStaticSourceDirectory(generatedAndroidResourceDir.absolutePath)
    }
}

multiplatformResources {
    resourcesPackage.set("yokai.i18n")
}

tasks {
   val localesConfigTask = project.registerLocalesConfigTask(generatedAndroidResourceDir)
   matching { it.name.contains("Resources") }.configureEach {
       dependsOn(localesConfigTask)
   }

    val verifyTranslations = register("verifyTranslations") {
        group = "verification"
        description = "Checks that every supported locale defines every base string and plural."

        doLast {
            val resourcesDir = file("src/commonMain/moko-resources")
            val baseStrings = translationKeys(resourcesDir.resolve("base/strings.xml"), "string")
            val basePlurals = translationKeys(resourcesDir.resolve("base/plurals.xml"), "plurals")

            val incompleteLocales = resourcesDir.listFiles()
                .orEmpty()
                .filter(File::isDirectory)
                .filterNot { it.name == "base" }
                .mapNotNull { localeDir ->
                    val missingStrings = baseStrings - translationKeys(localeDir.resolve("strings.xml"), "string")
                    val missingPlurals = basePlurals - translationKeys(localeDir.resolve("plurals.xml"), "plurals")
                    localeDir.name.takeIf { missingStrings.isNotEmpty() || missingPlurals.isNotEmpty() }?.let {
                        "$it (strings: ${missingStrings.size}, plurals: ${missingPlurals.size})"
                    }
                }

            check(incompleteLocales.isEmpty()) {
                "Incomplete translations:\n${incompleteLocales.joinToString("\n")}" 
            }
        }
    }

    named("check") {
        dependsOn(verifyTranslations)
    }

    withType<KotlinCompile> {
        compilerOptions.freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
        )
    }
}

private fun translationKeys(file: File, element: String): Set<String> {
    if (!file.isFile) return emptySet()
    return Regex("<$element\\s+name=\"([^\"]+)\"")
        .findAll(file.readText())
        .map { it.groupValues[1] }
        .toSet()
}
