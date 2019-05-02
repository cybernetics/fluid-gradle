package com.github.fluidsonic.fluid.library

import com.github.benmanes.gradle.versions.VersionsPlugin
import org.gradle.api.Project
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.kotlin.dsl.*


class FluidLibraryConfiguration private constructor(
	private val project: Project
) {

	var gradleVersion = "5.4.1"
	var name = ""
	var version = ""


	private fun Project.configureBasics() {
		val libraryName = this@FluidLibraryConfiguration.name
		val libraryVersion = this@FluidLibraryConfiguration.version

		check(libraryName.isNotEmpty()) { "'name' must be set" }
		check(libraryVersion.isNotEmpty()) { "'version' must be set" }

		apply<VersionsPlugin>()

		repositories {
			mavenCentral()
			jcenter()
			bintray("fluidsonic/maven")
			bintray("kotlin/kotlin-eap")
			bintray("kotlin/kotlinx")
		}

		tasks.withType<Wrapper> {
			this.distributionType = Wrapper.DistributionType.ALL
			this.gradleVersion = this@FluidLibraryConfiguration.gradleVersion
		}

		extensions.add("fluid-library", FluidLibraryPluginExtension(
			name = libraryName,
			version = libraryVersion
		))
	}


	private fun configureProject(): Unit = project.run {
		configureBasics()
	}


	companion object {

		internal fun applyTo(project: Project, configure: FluidLibraryConfiguration.() -> Unit = {}) {
			check(project.parent == null) { "fluidLibrary {} must only be used in the root project" }
			check(project.extensions.findByType<FluidLibraryPluginExtension>() == null) { "fluidLibrary/fluidJvmLibrary {} must only be used once" }

			FluidLibraryConfiguration(project = project).apply(configure).configureProject()
		}
	}
}
