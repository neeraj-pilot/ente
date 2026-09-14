import com.ncorti.ktfmt.gradle.KtfmtExtension

plugins {
    id("com.android.application") version "8.10.1" apply false
    id("com.android.library") version "8.10.1" apply false
    id("com.ncorti.ktfmt.gradle") version "0.27.0"
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
}

allprojects {
    plugins.withId("com.ncorti.ktfmt.gradle") {
        extensions.configure<KtfmtExtension> {
            kotlinLangStyle()
            maxWidth.set(100)
        }
    }
}
