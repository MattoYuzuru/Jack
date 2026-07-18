plugins {
	java
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.owasp.dependencycheck") version "12.2.2"
	id("org.cyclonedx.bom") version "3.2.4"
}

group = "com.keykomi"
version = "0.0.1-SNAPSHOT"
val targetJavaVersion = JavaLanguageVersion.of(26)

// Boot 4.1.0 вышел раньше части июльских security-релизов, поэтому временно
// переопределяем только версии из его BOM, для которых уже опубликованы исправления.
extra["jackson-2-bom.version"] = "2.22.1"
extra["log4j2.version"] = "2.26.1"
extra["postgresql.version"] = "42.7.13"
extra["tomcat.version"] = "11.0.24"

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
	implementation("org.flywaydb:flyway-core")
	runtimeOnly("org.flywaydb:flyway-database-postgresql")
	implementation("com.fasterxml.jackson.core:jackson-databind")
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
	implementation("org.apache.pdfbox:pdfbox:3.0.8")
	implementation("org.apache.poi:poi-ooxml:5.4.1")
	implementation("org.apache.poi:poi-scratchpad:5.4.1")
	implementation("org.jsoup:jsoup:1.18.3")
	implementation("com.vladsch.flexmark:flexmark:0.64.8")
	implementation("com.vladsch.flexmark:flexmark-ext-anchorlink:0.64.8")
	implementation("com.vladsch.flexmark:flexmark-ext-autolink:0.64.8")
	implementation("com.vladsch.flexmark:flexmark-ext-definition:0.64.8")
	implementation("com.vladsch.flexmark:flexmark-ext-footnotes:0.64.8")
	implementation("com.vladsch.flexmark:flexmark-ext-gfm-strikethrough:0.64.8")
	implementation("com.vladsch.flexmark:flexmark-ext-gfm-tasklist:0.64.8")
	implementation("com.vladsch.flexmark:flexmark-ext-ins:0.64.8")
	implementation("com.vladsch.flexmark:flexmark-ext-superscript:0.64.8")
	implementation("com.vladsch.flexmark:flexmark-ext-tables:0.64.8")
	implementation("com.vladsch.flexmark:flexmark-ext-toc:0.64.8")
	implementation("org.apache.commons:commons-csv:1.14.1")
	implementation("com.drewnoakes:metadata-extractor:2.19.0")
	implementation("org.apache.commons:commons-imaging:1.0.0-alpha6")
	implementation("org.jaudiotagger:jaudiotagger:2.0.1")
	implementation("org.yaml:snakeyaml:2.4")
	implementation("org.xerial:sqlite-jdbc")
	compileOnly("org.projectlombok:lombok")
	runtimeOnly("org.postgresql:postgresql")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
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

dependencyCheck {
	failBuildOnCVSS = 7.0F
	formats = listOf("HTML", "JSON")
	nvd.apiKey = System.getenv("NVD_API_KEY")
	// Стабильный путь позволяет CI переносить локальную NVD-базу между ephemeral runner'ами.
	data.directory = gradle.gradleUserHomeDir.resolve("dependency-check-data").absolutePath
}
