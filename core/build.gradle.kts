import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin: protocol, rules and data models with no Android dependency, so they test in plain JVM speed
// and can never reach for a Context by accident.
plugins {
    id("org.jetbrains.kotlin.jvm")
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
