import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL

plugins {
    `maven-publish`
    signing
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("org.jetbrains.dokka") version "1.9.20"
    id("pl.allegro.tech.build.axion-release") version "1.18.2"
}

val tagVersion = System.getenv("GITHUB_REF")?.split('/')?.last()
project.version = scmVersion.version

android {
    namespace = "io.getunleash.android"
    compileSdk = 34

    defaultConfig {
        minSdk = 21

        aarMetadata {
            minCompileSdk = 29
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("proguard-rules.pro")
    }

    buildTypes {
        debug {

        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    publishing {
        multipleVariants {
            includeBuildTypeValues("debug", "release")
            allVariants()
            withJavadocJar()
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.work.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.core)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.datatype.jsr310)
    api(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.assertj)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito)
    testImplementation(libs.robolectric.test)
    testImplementation(libs.okhttp.mockserver)
    testImplementation(libs.awaitility)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.assertj)
    androidTestImplementation(libs.mockito)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.okhttp.mockserver)
}

publishing {
    repositories {
        repositories {
            maven {
                url = uri(layout.buildDirectory.dir("repo"))
                name = "test"
            }
        }
        mavenLocal()
    }

    publications {
        afterEvaluate {
            create<MavenPublication>("mavenJava") {
                from(components["release"])
                groupId = "io.getunleash"
                artifactId = "unleash-android"
                version = version
                pom {
                    name.set("Unleash Android")
                    description.set("Android SDK for Unleash")
                    url.set("https://gh.getunleash.io/unleash-android")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("gastonfournier")
                            name.set("Gastón Fournier")
                            email.set("gaston@getunleash.io")
                        }
                        developer {
                            id.set("chrkolst")
                            name.set("Christopher Kolstad")
                            email.set("chriswk@getunleash.io")
                        }
                        developer {
                            id.set("ivarconr")
                            name.set("Ivar Conradi Østhus")
                            email.set("ivarconr@getunleash.io")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/Unleash/unleash-android")
                        developerConnection.set("scm:git:ssh://git@github.com:Unleash/unleash-android")
                        url.set("https://github.com/Unleash/unleash-android")
                    }
                }
            }
        }
    }
}

val signingKey: String? by project
val signingPassphrase: String? by project
signing {
    if (signingKey != null && signingPassphrase != null) {
        useInMemoryPgpKeys(signingKey, signingPassphrase)
        sign(publishing.publications)
    }
}

tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets {
        named("main") {
            moduleName.set("Unleash Android SDK")
            sourceLink {
                localDirectory.set(file("unleashandroidsdk/src/main/java"))
                remoteUrl.set(URL("https://github.com/Unleash/unleash-android/tree/${tagVersion ?: "main"}/unleashandroidsdk/src/main/java"))
                remoteLineSuffix.set("#L")
            }
        }
    }
}