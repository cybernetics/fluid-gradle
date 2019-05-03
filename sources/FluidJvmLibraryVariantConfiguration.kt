package com.github.fluidsonic.fluid.library

import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.BintrayPlugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.plugins.MavenRepositoryHandlerConvention
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.tasks.Upload
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.signing.SigningPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformJvmPlugin
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


class FluidJvmLibraryVariantConfiguration private constructor(
	private val project: Project
) {

	var enforcesSameVersionForAllKotlinDependencies = true
	var publishing = true
	var jdk = JDK.v1_7


	private fun Project.configureBintrayPublishing() {
		val bintrayUser = findProperty("bintrayUser") as String? ?: return
		val bintrayKey = findProperty("bintrayApiKey") as String? ?: return

		val library = fluidLibrary

		apply<BintrayPlugin>()
		configure<BintrayExtension> {
			user = bintrayUser
			key = bintrayKey

			setPublications("default")

			pkg.apply {
				repo = "maven"
				issueTrackerUrl = "https://github.com/fluidsonic/${library.name}/issues"
				name = library.name
				publicDownloadNumbers = true
				publish = true
				vcsUrl = "https://github.com/fluidsonic/${library.name}"
				websiteUrl = "https://github.com/fluidsonic/${library.name}"
				setLicenses("Apache-2.0")

				version.apply {
					name = library.version
					vcsTag = library.version
				}
			}
		}
	}


	private fun Project.configureBasics() {
		apply<KotlinPlatformJvmPlugin>()
		apply<JavaLibraryPlugin>()

		group = "com.github.fluidsonic"
		version = fluidLibrary.version

		if (enforcesSameVersionForAllKotlinDependencies)
			configurations {
				all {
					resolutionStrategy.eachDependency {
						if (requested.group == "org.jetbrains.kotlin") {
							useVersion(getKotlinPluginVersion()!!)
							because("All Kotlin modules must have the same version.")
						}
					}
				}
			}

		dependencies {
			api(platform(kotlin("bom")))
			api(kotlin("stdlib-${jdk.moduleId}"))
		}

		java {
			sourceCompatibility = jdk.toGradle()
			targetCompatibility = jdk.toGradle()
		}

		sourceSets {
			getByName("main") {
				kotlin.setSrcDirs(listOf("sources"))
				resources.setSrcDirs(listOf("resources"))
			}

			getByName("test") {
				kotlin.setSrcDirs(listOf("tests/sources"))
				resources.setSrcDirs(listOf("tests/resources"))
			}
		}

		tasks {
			withType<KotlinCompile> {
				sourceCompatibility = jdk.toString()
				targetCompatibility = jdk.toString()

				kotlinOptions.freeCompilerArgs = listOf("-Xuse-experimental=kotlin.contracts.ExperimentalContracts")
				kotlinOptions.jvmTarget = jdk.toKotlinTarget()
			}
		}

		repositories {
			mavenCentral()
			jcenter()
			bintray("fluidsonic/maven")
			bintray("kotlin/kotlin-eap")
			bintray("kotlin/kotlinx")
		}
	}


	private fun Project.configureSonatypePublishing() {
		val sonatypeUserName = findProperty("sonatypeUserName") as String? ?: return
		val sonatypePassword = findProperty("sonatypePassword") as String? ?: return

		val library = fluidLibrary

		signing {
			sign(configurations.archives.get())
		}

		tasks.getByName<Upload>("uploadArchives") {
			repositories {
				withConvention(MavenRepositoryHandlerConvention::class) {
					mavenDeployer {
						beforeDeployment {
							signing.signPom(this)
						}

						withGroovyBuilder {
							"repository"("url" to "https://oss.sonatype.org/service/local/staging/deploy/maven2") {
								"authentication"("userName" to sonatypeUserName, "password" to sonatypePassword)
							}

							"snapshotRepository"("url" to "https://oss.sonatype.org/content/repositories/snapshots") {
								"authentication"("userName" to sonatypeUserName, "password" to sonatypePassword)
							}
						}

						pom.project {
							withGroovyBuilder {
								"name"(project.name)
								"description"(project.description)
								"packaging"("jar")
								"url"("https://github.com/fluidsonic/${library.name}")
								"developers" {
									"developer" {
										"id"("fluidsonic")
										"name"("Marc Knaup")
										"email"("marc@knaup.io")
									}
								}
								"licenses" {
									"license" {
										"name"("Apache License 2.0")
										"url"("https://github.com/fluidsonic/${library.name}/blob/master/LICENSE")
									}
								}
								"scm" {
									"connection"("scm:git:https://github.com/fluidsonic/${library.name}.git")
									"developerConnection"("scm:git:git@github.com:fluidsonic/${library.name}.git")
									"url"("https://github.com/fluidsonic/${library.name}")
								}
							}
						}
					}
				}
			}
		}
	}


	private fun configureProject(): Unit = project.run {
		configureBasics()

		if (this@FluidJvmLibraryVariantConfiguration.publishing)
			configurePublishing()
	}


	private fun Project.configurePublishing() {
		apply<MavenPlugin>()
		apply<MavenPublishPlugin>()
		apply<PublishingPlugin>()
		apply<SigningPlugin>()

		val javadocJar by tasks.creating(Jar::class) {
			archiveClassifier.set("javadoc")
			from(tasks["javadoc"])
		}

		val sourcesJar by tasks.creating(Jar::class) {
			archiveClassifier.set("sources")
			from(sourceSets["main"].allSource, file("build/generated/source/kaptKotlin/main"))
		}

		artifacts {
			archives(javadocJar)
			archives(sourcesJar)
		}

		publishing {
			publications {
				create<MavenPublication>("default") {
					from(components["java"])
					artifact(sourcesJar)
				}
			}
		}

		configureBintrayPublishing()
		configureSonatypePublishing()
	}


	var description
		get() = project.description
		set(value) {
			project.description = value
		}


	companion object {

		internal fun applyTo(project: Project, configure: FluidJvmLibraryVariantConfiguration.() -> Unit = {}) {
			FluidJvmLibraryVariantConfiguration(project = project).apply(configure).configureProject()
		}
	}
}