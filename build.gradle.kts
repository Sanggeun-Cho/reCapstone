plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com"
version = "0.0.1-SNAPSHOT"
description = "reCapstone"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

extra["springAiVersion"] = "2.0.1"
extra["mybatisVersion"] = "4.1.0"
extra["springdocVersion"] = "3.2.0"
extra["greenmailVersion"] = "2.1.3"
extra["datasourceProxyVersion"] = "1.11.1"
extra["awsSdkVersion"] = "2.57.8"
extra["tikaVersion"] = "4.2.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-session-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:${property("mybatisVersion")}")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${property("springdocVersion")}")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:apache-client")
    implementation("org.apache.tika:tika-core:${property("tikaVersion")}")
    compileOnly("org.projectlombok:lombok")
    runtimeOnly("org.postgresql:postgresql")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("com.icegreen:greenmail:${property("greenmailVersion")}")
    testImplementation("net.ttddyy:datasource-proxy:${property("datasourceProxyVersion")}")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
        mavenBom("software.amazon.awssdk:bom:${property("awsSdkVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// AWS 에뮬레이터로 저장소 동작을 확인하는 적합성 테스트는 기본 테스트에서 빼고 따로 돌린다 (DESIGN_REVIEW.md 6.8)
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("s3-conformance")
    }
}

tasks.register<Test>("conformanceTest") {
    description = "AWS 에뮬레이터를 대상으로 저장소 적합성 테스트를 돌린다. EMULATOR_HOME, EMULATOR_ENDPOINT가 필요하다."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("s3-conformance")
    }
    // 에뮬레이터의 자체 서명 인증서는 실행마다 바뀌므로, 에뮬레이터가 쓴 trust store를 테스트 JVM에만 지정한다
    System.getenv("EMULATOR_HOME")?.let { home ->
        jvmArgs("-Djavax.net.ssl.trustStore=$home/truststore.p12", "-Djavax.net.ssl.trustStoreType=PKCS12")
    }
}
