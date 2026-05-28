plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

android {
    namespace = "com.example.nckitconfig"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("proguard-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("com.fiveexceptions.nckit:nckit:1.0.1")
}

group = "com.example.nckitconfig"
version = "1.0.3"

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = group.toString()
            artifactId = "nckitconfig"
            version = project.version.toString()

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("NCKitConfig")
                description.set("Android wrapper library exposing NoiceClear API based on NCKit.")
                url.set("https://github.com/5Exceptions-Mobile-Team/NCKit_Android")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("5exceptions")
                        name.set("5Exceptions Mobile Team")
                    }
                }
                scm {
                    url.set("https://github.com/5Exceptions-Mobile-Team/NCKit_Android")
                }
            }
        }
    }

    repositories {
        mavenLocal()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/5Exceptions-Mobile-Team/NCKit_Android")
            credentials {
                username = (findProperty("gpr.user") as String?) ?: System.getenv("GPR_USER")
                password = (findProperty("gpr.key") as String?) ?: System.getenv("GPR_KEY")
            }
        }
    }
}