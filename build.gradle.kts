import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.ofSourceSet
import com.google.protobuf.gradle.plugins
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc
import org.gradle.kotlin.dsl.proto
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.30"
    kotlin("plugin.serialization") version "1.4.30"
    `java-library`
    id("com.google.protobuf") version "0.8.14"
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions.useIR = true

version = "0.1.0"
group = "com.prettybyte"

val coroutinesVersion = "1.4.2"
val arrow_version = "0.11.0"
val exposedVersion = "0.28.1"
val ktorVersion = "1.5.1"
val graphqlKotlinVersion = "4.0.0-alpha.12"

val grpcVersion = "1.34.0"
val grpcKotlinVersion = "1.0.0"
val protobufVersion = "3.14.0"

repositories {
    mavenCentral()
    maven("https://dl.bintray.com/arrow-kt/arrow-kt/")
    jcenter()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("io.arrow-kt:arrow-core:$arrow_version")
    implementation("io.arrow-kt:arrow-syntax:$arrow_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.xerial:sqlite-jdbc:3.34.0")
    implementation("io.jsonwebtoken:jjwt-api:0.11.2")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.2")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.4.21")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    api("io.ktor:ktor-server-netty:$ktorVersion")
    api("com.expediagroup:graphql-kotlin-server:$graphqlKotlinVersion")
    implementation("com.expediagroup:graphql-kotlin-schema-generator:$graphqlKotlinVersion")


    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    //implementation("javax.annotation:javax.annotation-api:1.3.2")
    runtimeOnly("io.grpc:grpc-netty:$grpcVersion")
}

java {
    withSourcesJar()
}

tasks.jar {
    manifest {
        attributes(
            mapOf(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version
            )
        )
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}


sourceSets {
    main {
        proto {
            srcDir("src/main/kotlin/com/prettybyte/simplebackend/lib/proto")
        }
        java {
            srcDirs("build/generated/source/proto/main/grpc")
            srcDirs("build/generated/source/proto/main/java")
            srcDirs("build/generated/source/proto/main/grpckt")
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk7@jar"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.plugins {
                id("grpc")
                id("grpckt")
            }
        }
    }
}
