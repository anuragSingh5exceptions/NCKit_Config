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
version = (findProperty("libVersion") as String?) ?: "1.0.3"

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
                url.set("https://github.com/anuragSingh5exceptions/NCKit_Config")
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
                    connection.set("scm:git:git://github.com/anuragSingh5exceptions/NCKit_Config.git")
                    developerConnection.set("scm:git:ssh://github.com:anuragSingh5exceptions/NCKit_Config.git")
                    url.set("https://github.com/anuragSingh5exceptions/NCKit_Config")
                }
            }
        }
    }

    repositories {
        mavenLocal()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/anuragSingh5exceptions/NCKit_Config")
            credentials {
                username = (findProperty("gpr.user") as String?) ?: System.getenv("GPR_USER")
                password = (findProperty("gpr.key") as String?) ?: System.getenv("GPR_KEY")
            }
        }
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    doFirst {
        if (repository.name == "GitHubPackages" && project.version.toString() == "1.0.0") {
            throw GradleException(
                "Version 1.0.0 is already published. Use -PlibVersion=<new-version>, for example -PlibVersion=1.0.4"
            )
        }
    }
}