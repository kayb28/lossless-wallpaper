// Plugins a nivel raíz. Nota: con AGP 9 el compilador de Kotlin viene integrado
// en el plugin de Android (no se aplica org.jetbrains.kotlin.android aparte).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
