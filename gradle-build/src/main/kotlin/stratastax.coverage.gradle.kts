plugins {
    base
    id("stratastax.repositories")
    id("org.jetbrains.kotlinx.kover")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kover {
    useJacoco(libs.findVersion("jacoco").get().requiredVersion)

    reports {
        filters { excludes { annotatedBy("*Generated*") } }

        total {
            xml { onCheck = true }
            html { onCheck = true }
        }
    }
}
