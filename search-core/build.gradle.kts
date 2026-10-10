plugins {
    id("stratastax.library")
}

description = "Core module for the generic search API used throughout Stratastax"

dependencies {
    // stratastax
    implementation(libs.stratastax.entity)
    implementation(libs.kotlin.logging)
    testImplementation(libs.bundles.test)
}
