apply plugin: "java"

version '1.0'

targetCompatibility = 17
sourceCompatibility = JavaVersion.VERSION_17

sourceSets.main.java.srcDirs = ["src"]

repositories {
    mavenCentral()
    maven { url 'https://www.jitpack.io' }
}

ext {
    //the build number that this mod is made for
    mindustryVersion = 'v150.1'
    jabelVersion = "93fde537c7"
    sdkRoot = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
}

//java 8 backwards compatibility flag
allprojects {
    tasks.withType(JavaCompile).tap {
        configureEach {
            options.compilerArgs.addAll(['--release', '8'])
        }
    }
}

dependencies {
    compileOnly "com.github.Anuken.Arc:arc-core:$mindustryVersion"
    compileOnly "com.github.Anuken.MindustryJitpack:core:$mindustryVersion"

    annotationProcessor "com.github.Anuken:jabel:$jabelVersion"
}

//force arc version
configurations.all {
    resolutionStrategy.eachDependency { details ->
        if(details.requested.group == 'com.github.Anuken.Arc') {
            details.useVersion "$mindustryVersion"
        }
    }
}

task jarAndroid {
    dependsOn "jar"

    doLast {
        if(!sdkRoot || !new File(sdkRoot).exists()) throw new GradleException("No valid Android SDK found. Ensure that ANDROID_HOME is set to your Android SDK directory.");

        def platformRoot = new File("$sdkRoot/platforms/").listFiles().sort().reverse().find { f -> new File(f, "android.jar").exists()}

        if(!platformRoot) throw new GradleException("No android.jar found. Ensure that you have an Android platform installed.")

        //collect dependencies needed for desugaring
        def dependencies = (configurations.compileClasspath.asList() + configurations.runtimeClasspath.asList() + [new File(platformRoot, "android.jar")]).collect { "--classpath $it.path" }.join(" ")

        //dex and desugar files - this requires d8 in your PATH
        doExec("d8 $dependencies --min-api 14 --output ${project.archivesBaseName}Android.jar ${project.archivesBaseName}Desktop.jar")
    }
}

task deploy(type: Jar) {
    dependsOn jarAndroid
    dependsOn jar
    archiveFileName = "${project.archivesBaseName}.jar"

    from { [zipTree("$buildDir/libs/${project.archivesBaseName}Desktop.jar"), zipTree("$buildDir/libs/${project.archivesBaseName}Android.jar")] }

    doLast {
        delete { delete "$buildDir/libs/${project.archivesBaseName}Desktop.jar" }
        delete { delete "$buildDir/libs/${project.archivesBaseName}Android.jar" }
    }
}

jar {
    archiveFileName = "${project.archivesBaseName}Desktop.jar"

    from(rootDir) {
        include "mod.hjson"
        include "icon.png"
    }

    from("assets/") {
        include "**"
    }
}

void doExec(String... args) {
    def proc = args.execute(null, projectDir)
    proc.waitFor()
    
    if(proc.exitValue() != 0) {
        def error = proc.err.text
        if(error) {
            throw new GradleException("Error executing command: $args\n$error")
        }
    }
}
