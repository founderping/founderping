plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
    signing
}

android {
    namespace = "app.founderping.widget"
    compileSdk = 34

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
    }

    // A failure the library catches is logged through android.util.Log, which
    // throws on the JVM unless the framework stubs answer with defaults.
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.json)
}

// Maven Central takes one zip of the signed Maven layout, which publish.sh
// uploads: the publication goes to this local repository, `centralBundle` zips it.
val centralStaging = layout.buildDirectory.dir("central/staging")

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "app.founderping"
                artifactId = "widget"
                version = "0.1.0"
                pom {
                    name = "FounderPing for Android"
                    description = "Runs the FounderPing feedback widget inside an Android app."
                    url = "https://founderping.app"
                    licenses {
                        license {
                            name = "MIT License"
                            url = "https://opensource.org/licenses/MIT"
                        }
                    }
                    developers {
                        developer {
                            name = "FounderPing"
                            email = "hello@founderping.app"
                            url = "https://founderping.app"
                        }
                    }
                    scm {
                        url = "https://github.com/founderping/founderping"
                        connection = "scm:git:https://github.com/founderping/founderping.git"
                        developerConnection = "scm:git:ssh://git@github.com/founderping/founderping.git"
                    }
                }
            }
        }
        repositories {
            maven {
                name = "centralStaging"
                url = uri(centralStaging)
            }
        }
    }

    // Signed by the gpg binary, because Gradle's built-in signer leaves out the
    // issuer fingerprint that Central looks the public key up by. Only
    // publish.sh sets up a keyring, so every other build, publishToMavenLocal
    // included, runs without secrets.
    providers.environmentVariable("SIGNING_GNUPG_HOME").orNull?.let { gnupgHome ->
        extra["signing.gnupg.homeDir"] = gnupgHome
        extra["signing.gnupg.passphrase"] = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
        signing {
            useGpgCmd()
            sign(publishing.publications["release"])
        }
    }
}

tasks.register<Zip>("centralBundle") {
    dependsOn("publishReleasePublicationToCentralStagingRepository")
    from(centralStaging) { exclude("**/maven-metadata.xml*") }
    destinationDirectory = layout.buildDirectory.dir("central")
    archiveFileName = "bundle.zip"
}
