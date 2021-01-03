//============================================================================//
//                                                                            //
//                         Copyright © 2015 Sandpolis                         //
//                                                                            //
//  This source file is subject to the terms of the Mozilla Public License    //
//  version 2. You may not use this file except in compliance with the MPL    //
//  as published by the Mozilla Foundation.                                   //
//                                                                            //
//============================================================================//
package com.sandpolis.agent.vanilla.config;

import static com.sandpolis.core.net.connection.ConnectionStore.ConnectionStore;

import java.io.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

import com.sandpolis.agent.vanilla.cmd.AuthCmd;
import com.sandpolis.core.foundation.util.ValidationUtil;
import com.sandpolis.core.instance.Environment;
import com.sandpolis.core.net.connection.Connection;

public class InteractiveConfig {

	private Properties config;
	private String serverAddress;
	private int serverPort = 8768;
	private Connection connection;

	public void run() throws IOException {

		Console console = System.console();
		if (console == null) {
			throw new RuntimeException();
		}

		// Load existing configuration
		config = new Properties();
		try (var in = Files.newInputStream(Environment.CFG.path().resolve("config.properties"))) {
			config.load(in);
		} catch (IOException e) {
			// Start new config
		}

		while (true) {
			console.format("Enter server address [%s]: ", "");
			serverAddress = console.readLine();

			String[] components = serverAddress.split(":");
			if (components.length >= 2) {
				if (!ValidationUtil.port(components[1])) {
					console.format("Invalid port%n");
					continue;
				}

				serverAddress = components[0];
				serverPort = Integer.parseInt(components[1]);
			}

			if (!ValidationUtil.address(serverAddress)) {
				console.format("Invalid address%n");
				continue;
			}

			// Attempt connection
			try {
				connection = ConnectionStore.connect(serverAddress, serverPort).get();
			} catch (Exception e) {
				console.format("Conection failed; continue anyway? [Y]: ");
				switch (console.readLine()) {
				case "":
				case "Y":
				case "y":
					break;
				}
				continue;
			}

			// Retrieve banner
			// ServerCmd.async().target(connection).getBanner().toCompletableFuture().join()

			// Attempt to authenticate
			AuthCmd.async().target(connection).none();

			break;
		}

		// Store server address
		config.setProperty("", serverAddress + ":" + serverPort);

		try (var out = Files.newOutputStream(Environment.CFG.path().resolve("config.properties"))) {
			config.store(out, null);
		}
	}
}
