rootProject.name = "substrait"

include("bom", "core", "isthmus", "isthmus-cli")

pluginManagement {
  plugins {
    fun String.v() = extra["$this.version"].toString()
    fun PluginDependenciesSpec.idv(id: String, key: String = id) = id(id) version key.v()

    idv("com.google.protobuf")
    idv("org.jetbrains.gradle.plugin.idea-ext")
    kotlin("jvm") version "kotlin".v()
  }
}
