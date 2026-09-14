import com.ncorti.ktfmt.gradle.KtfmtExtension
import dev.detekt.gradle.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    id("com.android.application") version "8.10.1" apply false
    id("com.android.library") version "8.10.1" apply false
    id("com.ncorti.ktfmt.gradle") version "0.27.0"
    id("dev.detekt") version "2.0.0-alpha.6"
    id("org.jetbrains.kotlin.jvm") version "2.4.20" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }

    apply(plugin = "com.ncorti.ktfmt.gradle")
    apply(plugin = "dev.detekt")
    extensions.configure<KtfmtExtension> {
        kotlinLangStyle()
        maxWidth.set(100)
    }

    extensions.configure<DetektExtension> { config.setFrom(rootProject.file("detekt.yml")) }

    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions.allWarningsAsErrors.set(true)
    }
}

tasks.register("lint") {
    dependsOn(
        allprojects.map { project ->
            project.tasks.matching {
                it.name in listOf("ktfmtCheck", "detektMain", "detektTest", "lintDebug")
            }
        }
    )
}
