import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(17)
}

// Configure project's dependencies
repositories {
    mavenLocal()
    maven {
        url = uri("https://maven.aliyun.com/repository/public/")
        isAllowInsecureProtocol = false
    }
    maven {
        url = uri("https://maven.aliyun.com/repository/jcenter/")
        isAllowInsecureProtocol = false
    }
    maven {
        url = uri("https://maven.aliyun.com/repository/google/")
        isAllowInsecureProtocol = false
    }
    maven {
        url = uri("https://maven.aliyun.com/repository/gradle-plugin/")
        isAllowInsecureProtocol = false
    }

    mavenCentral()
    google()
    maven { url = uri("https://plugins.gradle.org/m2/") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/releases/") }
    maven { url = uri("https://dl.bintray.com/jetbrains/intellij-plugin-service") }
    maven { url = uri("https://dl.bintray.com/jetbrains/intellij-third-party-dependencies/") }

    maven { url = uri("https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases") }
    maven { url = uri("https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/snapshots") }
    // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {
        defaultRepositories()
    }
}

// Disable proxy settings to bypass the connection to 127.0.0.1:33210
//System.setProperty("http.proxyHost", "")
//System.setProperty("http.proxyPort", "")
//System.setProperty("https.proxyHost", "")
//System.setProperty("https.proxyPort", "")
//System.setProperty("http.nonProxyHosts", "*")


// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
    testImplementation(libs.junit)
    compileClasspath(libs.lombok)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // 读取 ideaLocalPath 属性
        val localPath = providers.gradleProperty("ideaLocalPath").orElse("").get()
        if (localPath.isNotEmpty()) {
            // 本地路径不为空,使用本地路径下的平台调试
            local(localPath)
        } else {
            // 使用远程版本调试， 需要指定 platformType 和 platformVersion
            val platformType = providers.gradleProperty("platformType").get()
            val platformVersion = providers.gradleProperty("platformVersion").get()
            if (platformType.isNotEmpty() && platformVersion.isNotEmpty()) {
                // 使用远程 版本
                create(platformType, platformVersion)
            } else {
                // 都没有，报异常
                throw GradleException("必须指定 ideaLocalPath 或同时指定 platformType 和 platformVersion")
            }
        }

        // 插件依赖项。对于捆绑的 IntelliJ Platform 插件，使用 gradle.properties 文件中的 'platformBundledPlugins' 属性。
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        // 插件依赖项。对于 JetBrains Marketplace 插件，使用 gradle.properties 文件中的 'platformPlugins' 属性。
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        // 插件调试工具
        pluginVerifier()
        // 签名工具
        zipSigner()
        // 测试框架
        testFramework(TestFrameworkType.Platform)
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = providers.gradleProperty("pluginVersion")
            .map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    // 设置Java编译任务的编码为UTF-8
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    // 对于Kotlin，IntelliJ Platform Gradle Plugin会自动配置编码
    // 但我们可以通过intellijPlatform扩展确保编码设置正确
    publishPlugin {
        dependsOn(patchChangelog)
    }
}

intellijPlatformTesting {
    runIde {
        register("runIdeForUiTests") {
            task {
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-Drobot-server.port=8082",
                        "-Dide.mac.message.dialogs.as.sheets=false",
                        "-Djb.privacy.policy.text=<!--999.999-->",
                        "-Djb.consents.confirmation.enabled=false",
                    )
                }
            }

            plugins {
                robotServerPlugin()
            }
        }
    }
}
