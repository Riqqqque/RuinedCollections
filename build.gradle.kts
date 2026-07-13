import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    java
    id("com.gradleup.shadow") version "9.4.2"
}

group = "com.rique"
version = providers.gradleProperty("pluginVersion").get()

val paperApiVersion = providers.gradleProperty("paperApiVersion").get()
val hikariVersion = providers.gradleProperty("hikariVersion").get()
val sqliteJdbcVersion = providers.gradleProperty("sqliteJdbcVersion").get()
val mysqlConnectorVersion = providers.gradleProperty("mysqlConnectorVersion").get()
val protobufVersion = providers.gradleProperty("protobufVersion").get()
val bstatsVersion = providers.gradleProperty("bstatsVersion").get()

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly("me.clip:placeholderapi:2.12.3")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.bstats:bstats-bukkit:$bstatsVersion")

    compileOnly("org.xerial:sqlite-jdbc:$sqliteJdbcVersion")
    compileOnly("com.mysql:mysql-connector-j:$mysqlConnectorVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.2")
    testRuntimeOnly("org.xerial:sqlite-jdbc:$sqliteJdbcVersion")
}

configurations.named("compileClasspath") {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.processResources {
    val resourceProperties = mapOf(
        "pluginVersion" to project.version.toString(),
        "sqliteJdbcVersion" to sqliteJdbcVersion,
        "mysqlConnectorVersion" to mysqlConnectorVersion,
        "protobufVersion" to protobufVersion
    )
    inputs.properties(resourceProperties)
    filesMatching("plugin.yml") {
        expand(resourceProperties)
    }
}

tasks.jar {
    archiveBaseName.set("RuinedCollections")
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveBaseName.set("RuinedCollections")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
    relocate("org.bstats", "com.rique.ruinedcollections.libs.bstats")
    relocate("com.zaxxer.hikari", "com.rique.ruinedcollections.libs.hikari")
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("module-info.class")
    exclude("META-INF/versions/*/module-info.class")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
