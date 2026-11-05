plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

// JitPack builds set GROUP / ARTIFACT / VERSION (com.github.FPJack / ZLRichText / the git tag).
// Publishing with exactly these coordinates keeps JitPack from renaming the files, which would
// break the sources entry of the Gradle module metadata (IDEs then show decompiled code).
// JitPack also passes -Pgroup / -Pversion, used when the variables are missing.
val onJitPack = System.getenv("JITPACK") == "true"
val publishGroup: String = if (onJitPack) System.getenv("GROUP") ?: group.toString() else "io.github.fpjack"
val publishArtifact: String = if (onJitPack) System.getenv("ARTIFACT") ?: rootProject.name else "zlrichtext"
val publishVersion: String = if (onJitPack) System.getenv("VERSION") ?: version.toString() else "1.1.1"
group = publishGroup
version = publishVersion

android {
    namespace = "com.zl.richtext"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    api(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

// No Gradle module metadata (.module): JitPack rewrites it and points its sources variant to a
// file that does not exist, so IDEs show decompiled code. With the POM only, Gradle finds the
// sources by the Maven convention (<name>-sources.jar).
tasks.withType<GenerateModuleMetadata>().configureEach { enabled = false }

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = publishGroup
            artifactId = publishArtifact
            version = project.version.toString()
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("ZLRichText")
                description.set("Rule / tag based rich text TextView for Android.")
                url.set("https://github.com/FPJack/ZLRichText")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
}
