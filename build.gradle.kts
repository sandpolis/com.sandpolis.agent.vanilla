//============================================================================//
//                                                                            //
//                         Copyright © 2015 Sandpolis                         //
//                                                                            //
//  This source file is subject to the terms of the Mozilla Public License    //
//  version 2. You may not use this file except in compliance with the MPL    //
//  as published by the Mozilla Foundation.                                   //
//                                                                            //
//============================================================================//

import com.bmuschko.gradle.docker.tasks.container.*
import com.bmuschko.gradle.docker.tasks.image.*

plugins {
	id("java-library")
	id("sandpolis-java")
	id("sandpolis-module")
	id("sandpolis-publish")
	id("sandpolis-soi")
	id("com.bmuschko.docker-remote-api") version "6.6.0"
}

dependencies {
	testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.1")
	testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")

	implementation(project(":module:com.sandpolis.core.agent"))

	// https://github.com/javaee/jpa-spec
	implementation("javax.persistence:javax.persistence-api:2.2")
}

task<Sync>("assembleLib") {
	dependsOn(tasks.named("jar"))
	from(configurations.runtimeClasspath)
	from(tasks.named("jar"))
	into("${buildDir}/lib")
}

task<DockerBuildImage>("buildImage") {
	dependsOn(tasks.named("assembleLib"))
	inputDir.set(file("."))
	images.add("sandpolis/agent/vanilla:${project.version}")
	images.add("sandpolis/agent/vanilla:latest")
}

task<Exec>("runImage") {
	dependsOn(tasks.named("buildImage"))
	commandLine("docker", "run", "--rm", "sandpolis/agent/vanilla:latest")
}
