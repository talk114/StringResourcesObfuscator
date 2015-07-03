package com.github.oliverjonas.stringresourcesobfuscator

import org.gradle.api.*

class Settings {
    List<String> files = [ "strings.xml" ]
    String sourceBuildType = "main"
    String targetBuildType = "release"
    int seed = 0
}

class StringResourcesObfuscatorPlugin implements Plugin<Project> {
    def void apply(Project project) {
        project.extensions.create("stringResourcesObfuscatorSettings", Settings)
        project.task('stringResourcesObfuscator', type: StringResourcesObfuscatorTask)
    }
}


