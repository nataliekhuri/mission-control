plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        // The application targets desktop only: persistence (SQLite via JDBC),
        // JSON import/export and the UI live in jvmMain.
        jvmMain.dependencies {
            implementation(libs.compose.materialIconsCore)
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.sqlite.jdbc)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
