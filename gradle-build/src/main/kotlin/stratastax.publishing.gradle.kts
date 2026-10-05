plugins {
    `maven-publish`
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

group = "dev.buijs.stratastax"
version = libs.findVersion("stratastax-search").get()