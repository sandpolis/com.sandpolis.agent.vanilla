//============================================================================//
//                                                                            //
//                         Copyright © 2015 Sandpolis                         //
//                                                                            //
//  This source file is subject to the terms of the Mozilla Public License    //
//  version 2. You may not use this file except in compliance with the MPL    //
//  as published by the Mozilla Foundation.                                   //
//                                                                            //
//============================================================================//

plugins {
	id("java-library")
	id("sandpolis-java")
	id("sandpolis-module")
	id("sandpolis-publish")
	id("sandpolis-soi")
	id("application")
}

application {
    mainModule.set("com.sandpolis.agent.vanilla")
    mainClass.set("com.sandpolis.agent.vanilla.Main")
}

tasks.named<JavaExec>("run") {
    environment.put("S7S_DEVELOPMENT_MODE", "true")
	environment.put("S7S_LOG_LEVELS", "io.netty=WARN,java.util.prefs=OFF,com.sandpolis=TRACE")
}

dependencies {
	testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.2")
	testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.2")

	if (project.getParent() == null) {
		implementation("com.sandpolis:core.agent:+")
		implementation("com.sandpolis:core.net:+")
		implementation("com.sandpolis:core.instance:+")
	} else {
		implementation(project(":module:com.sandpolis.core.agent"))
		implementation(project(":module:com.sandpolis.core.net"))
		implementation(project(":module:com.sandpolis.core.instance"))
	}
}

task<Sync>("assembleLib") {
	dependsOn(tasks.named("jar"))
	from(configurations.runtimeClasspath)
	from(tasks.named("jar"))
	into("${buildDir}/lib")
}
