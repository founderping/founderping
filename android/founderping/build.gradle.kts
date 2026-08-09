plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "app.founderping.widget"
    compileSdk = 34

    publishing { singleVariant("release") { withSourcesJar() } }

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
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.json)
}

// `publishToMavenLocal` is how the sample app and any local integration test
// consume this, so the AAR they get is the one that would ship — consumer
// ProGuard rules included.
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "app.founderping"
                artifactId = "widget"
                version = "0.1.0"
            }
        }
    }
}
