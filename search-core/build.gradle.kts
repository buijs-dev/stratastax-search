plugins {
    id("stratastax.kotlin")
    id("stratastax.publishing")
    id("stratastax.repositories")
    id("stratastax.spotless")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            artifactId = "search-core"

            pom {
                name = "Stratastax Search Core"
                description = "Core module for the generic search API used throughout Stratastax"
                url = "https://github.com/buijs-dev/search-core"

                licenses {
                    license {
                        name = "MIT License"
                        url = "https://github.com/buijs-dev/search-core/blob/main/LICENSE"
                    }
                }

                developers {
                    developer {
                        id = "buijs-dev"
                        name = "Gillian Buijs"
                        email = "info@buijs.dev"
                    }
                }

                scm {
                    connection = "scm:git:https://github.com/buijs-dev/search-core.git"
                    developerConnection = "scm:git:ssh://git@github.com/buijs-dev/search-core.git"
                    url = "https://github.com/buijs-dev/search-core"
                }
            }
        }
    }
}

dependencies {
    // stratastax
    implementation(libs.stratastax.entity)

    //
    implementation(libs.kotlin.logging)

    //
    testImplementation(libs.bundles.test)
}
