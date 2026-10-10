plugins {
    id("stratastax.kotlin")
    id("stratastax.repositories")
    id("stratastax.publishing")
    id("stratastax.formatting")
    id("stratastax.coverage")
    id("stratastax.dokka")
    `java-library`
}

publishing {
    publications.create<MavenPublication>("mavenJava") { from(components["java"]) }
}
