//============================================================================//
//                                                                            //
//                         Copyright © 2015 Sandpolis                         //
//                                                                            //
//  This source file is subject to the terms of the Mozilla Public License    //
//  version 2. You may not use this file except in compliance with the MPL    //
//  as published by the Mozilla Foundation.                                   //
//                                                                            //
//============================================================================//
package com.sandpolis.agent.vanilla;

import static com.sandpolis.core.net.connection.ConnectionStore.ConnectionStore;

import java.io.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import com.sandpolis.agent.vanilla.cmd.AuthCmd;
import com.sandpolis.core.agent.config.CfgAgent;
import com.sandpolis.core.foundation.util.ValidationUtil;
import com.sandpolis.core.instance.Environment;
import com.sandpolis.core.net.connection.Connection;

/**
 * Obtain the configuration from the user via the terminal.
 */
public final class ConfigPrompter {

	private Console console = System.console();

	private final Predicate<String> YN_VALIDATOR = answer -> {
		switch (answer.toLowerCase()) {
		case "y":
		case "n":
			return true;
		default:
			console.format("Invalid option%n");
			return false;
		}
	};

	public void run() throws IOException {

		// Check for TTY
		if (console == null) {
			throw new RuntimeException("No console attached");
		}

		Properties config = new Properties();

		// Ask server address
		var server = prompt("Enter server address", "127.0.0.1", answer -> {

			var components = answer.split(":");
			if (components.length == 2) {
				// Check the explicit port
				if (!ValidationUtil.port(components[1])) {
					console.format("Invalid port: '%s'%n", components[1]);
					return false;
				}
			} else if (components.length != 1) {
				console.format("Invalid hostname%n");
				return false;
			}

			// Validate hostname
			if (!ValidationUtil.address(components[0])) {
				console.format("Invalid address%n");
				return false;
			}

			return true;
		});

		// Check for explicit port
		int port = 8768;
		if (server.contains(":")) {
			port = Integer.parseInt(server.substring(server.indexOf(':') + 1, server.length()));
			server = server.substring(0, server.indexOf(':'));
		}

		config.setProperty(CfgAgent.SERVER_ADDRESS.property(), server + ":" + port);

		// Attempt connection
		Connection connection;
		try {
			connection = ConnectionStore.connect(server, port).get();
		} catch (Exception e) {
			connection = null;
		}

		// Retrieve banner
		// var banner =
		// ServerCmd.async().target(connection).getBanner().toCompletableFuture().join();

		boolean configuredAuthentication = false;

		// Attempt authentication via client certificates
		if (!configuredAuthentication && prompt("Configure client certificate authentication?", false)) {
			configuredAuthentication = true;
			// TODO
		}

		// Attempt authentication via password
		if (!configuredAuthentication && prompt("Configure password authentication?", false)) {

			while (true) {
				var password = prompt("Enter password", "", answer -> {
					if (answer.length() < 5) {
						console.format("Password too short%n");
						return false;
					}
					return true;
				});
				config.setProperty("", password);

				if (connection != null) {
					try {
						if (!AuthCmd.async().target(connection).password(password).toCompletableFuture().get()
								.getResult()) {
							continue;
						}
					} catch (InterruptedException | ExecutionException e) {
						;
					}
				}

				configuredAuthentication = true;
				break;
			}

		}

		// Attempt "no authentication"
		if (!configuredAuthentication) {
			console.format("Warning: no authentication will be used (be careful)%n");
			if (connection != null) {
				try {
					if (!AuthCmd.async().target(connection).none().toCompletableFuture().get().getResult()) {
						console.format("Error: failed to authenticate%n");
					}
				} catch (InterruptedException | ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		// Store configuration
		try (var out = Files.newOutputStream(Environment.CFG.path().resolve("instance.properties"))) {
			config.store(out, null);
		}
	}

	/**
	 * Prompt for a yes/no answer.
	 *
	 * @param prompt        The prompt to show
	 * @param defaultAnswer The default answer
	 * @return The answer
	 */
	private synchronized boolean prompt(String prompt, boolean defaultAnswer) {
		switch (prompt(prompt, defaultAnswer ? "y" : "n", YN_VALIDATOR).toLowerCase()) {
		case "y":
			return true;
		case "n":
			return false;
		default:
			return false;
		}

	}

	/**
	 * Prompt for an answer.
	 *
	 * @param prompt        The prompt to show
	 * @param defaultAnswer The default answer
	 * @param validator     A predicate that determines whether the answer is valid
	 * @return The answer
	 */
	private synchronized String prompt(String prompt, String defaultAnswer, Predicate<String> validator) {
		String value;
		do {
			console.format("%n%s [%s]: ", prompt, defaultAnswer);
			value = console.readLine();
			if (value.isEmpty()) {
				return defaultAnswer;
			}
		} while (!validator.test(value));

		return value;
	}
}
