plugins {
    `maven-publish`
}

group = "dev.buijs.stratastax"

val repository = providers.gradleProperty("stratastax.repository").get()
val displayName = providers.gradleProperty("stratastax.displayName").get()
val githubUrl = "https://github.com/buijs-dev/$repository"

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.convention(if (project == rootProject) displayName else "$displayName - ${project.name}")
            description.convention(provider { project.description })
            url.convention(githubUrl)

            licenses {
                license {
                    name = "MIT License"
                    url = "$githubUrl/blob/main/LICENSE"
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
                connection = "scm:git:$githubUrl.git"
                developerConnection = "scm:git:ssh://git@github.com/buijs-dev/$repository.git"
                url = githubUrl
            }
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/buijs-dev/$repository")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }

        maven {
            name = "Repsy"
            url = uri("https://repo.repsy.io/mvn/buijs-dev/maven")
            credentials {
                username = providers.environmentVariable("REPSY_USERNAME").orNull
                password = providers.environmentVariable("REPSY_PASSWORD").orNull
            }
        }
    }
}
