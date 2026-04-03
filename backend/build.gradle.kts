plugins {
	java
	id("org.springframework.boot") version "4.0.5"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.keykomi"
version = "0.0.1-SNAPSHOT"
val targetJavaVersion = JavaLanguageVersion.of(26)

java {
	toolchain {
		languageVersion = targetJavaVersion
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	compileOnly("org.projectlombok:lombok")
	runtimeOnly("org.postgresql:postgresql")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("com.h2database:h2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}

// Для bootRun и других JavaExec-задач принудительно используем Java 26 toolchain,
// чтобы контейнер и локальная JDK 21 одинаково запускали приложение.
tasks.withType<JavaExec>().configureEach {
	javaLauncher = javaToolchains.launcherFor {
		languageVersion = targetJavaVersion
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
