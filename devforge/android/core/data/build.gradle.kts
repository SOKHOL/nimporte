import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.snakeyaml.engine)
    testImplementation(kotlin("test"))
}

// Les tests chargent le vrai contenu du dépôt : ils sont relancés quand il change.
tasks.test {
    val contenu = rootDir.resolve("../content")
    inputs.dir(contenu).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("devforge.content", contenu.canonicalPath)
}
