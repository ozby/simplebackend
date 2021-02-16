import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.30"
    kotlin("plugin.serialization") version "1.4.30"
}

val coroutinesVersion = "1.4.2"
val arrowVersion = "0.11.0"
val exposedVersion = "0.28.1"
val ktorVersion = "1.5.1"
val graphqlKotlinVersion = "4.0.0-alpha.12"


val grpcVersion = "1.34.0"
val grpcKotlinVersion = "1.0.0"
val protobufVersion = "3.14.0"

group = "me.linus"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(files("../../build/libs/simplebackend-0.1.0.jar"))
    // TODO: Don't want this all these dependencies. Why are they needed?
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
    implementation("io.jsonwebtoken:jjwt-api:0.11.2")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.2")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("io.arrow-kt:arrow-core:$arrowVersion")
    implementation("io.arrow-kt:arrow-syntax:$arrowVersion")
    testImplementation(kotlin("test-junit"))
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.xerial:sqlite-jdbc:3.34.0")
    runtimeOnly("io.ktor:ktor-server-netty:$ktorVersion")
    //implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.12.+")
    api("com.expediagroup:graphql-kotlin-server:$graphqlKotlinVersion")
    implementation("com.expediagroup:graphql-kotlin-schema-generator:$graphqlKotlinVersion")

    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    //implementation("javax.annotation:javax.annotation-api:1.3.2")
    runtimeOnly("io.grpc:grpc-netty:$grpcVersion")
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}