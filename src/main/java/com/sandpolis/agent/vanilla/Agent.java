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

import static com.sandpolis.core.instance.Environment.printEnvironment;
import static com.sandpolis.core.instance.MainDispatch.register;
import static com.sandpolis.core.instance.plugin.PluginStore.PluginStore;
import static com.sandpolis.core.instance.profile.ProfileStore.ProfileStore;
import static com.sandpolis.core.instance.state.InstanceOid.InstanceOid;
import static com.sandpolis.core.instance.state.STStore.STStore;
import static com.sandpolis.core.instance.thread.ThreadStore.ThreadStore;
import static com.sandpolis.core.net.connection.ConnectionStore.ConnectionStore;
import static com.sandpolis.core.net.exelet.ExeletStore.ExeletStore;
import static com.sandpolis.core.net.network.NetworkStore.NetworkStore;
import static com.sandpolis.core.net.stream.StreamStore.StreamStore;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;
import com.sandpolis.agent.vanilla.cmd.AuthCmd;
import com.sandpolis.agent.vanilla.exe.AgentExe;
import com.sandpolis.core.agent.config.CfgAgent;
import com.sandpolis.core.clientagent.cmd.PluginCmd;
import com.sandpolis.core.foundation.Platform.OsType;
import com.sandpolis.core.foundation.Result.Outcome;
import com.sandpolis.core.foundation.config.CfgFoundation;
import com.sandpolis.core.instance.Core;
import com.sandpolis.core.instance.Environment;
import com.sandpolis.core.instance.Group.AgentConfig;
import com.sandpolis.core.instance.Group.AgentConfig.LoopConfig;
import com.sandpolis.core.instance.Group.AgentConfig.NetworkTarget;
import com.sandpolis.core.instance.MainDispatch;
import com.sandpolis.core.instance.MainDispatch.InitializationTask;
import com.sandpolis.core.instance.MainDispatch.Task;
import com.sandpolis.core.instance.config.CfgInstance;
import com.sandpolis.core.instance.state.st.ephemeral.EphemeralDocument;
import com.sandpolis.core.net.channel.client.ClientChannelInitializer;
import com.sandpolis.core.net.network.NetworkEvents.ServerEstablishedEvent;
import com.sandpolis.core.net.network.NetworkEvents.ServerLostEvent;

import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.UnorderedThreadPoolEventExecutor;

/**
 * {@link Agent} is responsible for initializing the instance.
 *
 * @since 1.0.0
 */
public final class Agent {

	private static final Logger log = LoggerFactory.getLogger(Agent.class);

	public static void main(String[] args) {
		printEnvironment(log, "Sandpolis Agent");

		register(Agent.loadEnvironment);
		register(Agent.loadConfiguration);
		register(Agent.loadStores);
		register(Agent.loadPlugins);
		register(Agent.beginConnectionRoutine);
	}

	/**
	 * Load the instance's configuration.
	 */
	@InitializationTask(name = "Load agent configuration", fatal = true)
	public static final Task loadConfiguration = new Task(outcome -> {

		CfgInstance.PATH_LIB.register();
		CfgInstance.PATH_LOG.register();
		CfgInstance.PATH_PLUGIN.register();
		CfgInstance.PATH_TMP.register();
		CfgInstance.PATH_DATA.register();
		CfgInstance.PATH_CFG.register();

		CfgInstance.PLUGIN_ENABLED.register(true);

		if (CfgFoundation.DEVELOPMENT_MODE.value().orElse(false)) {
			CfgAgent.SERVER_ADDRESS.register("172.17.0.1");
			CfgAgent.SERVER_COOLDOWN.register(5000);
			CfgAgent.SERVER_TIMEOUT.register(5000);
		} else {

			// Check for configuration
			if (!Files.exists(Environment.CFG.path().resolve("config.properties"))) {
				log.info("Requesting configuration via user input");
				try {
					new ConfigPrompter().run();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}

			CfgAgent.SERVER_ADDRESS.require();
		}

		return outcome.success();
	});

	/**
	 * Load the runtime environment.
	 */
	@InitializationTask(name = "Load runtime environment", fatal = true)
	public static final Task loadEnvironment = new Task(outcome -> {

		Environment.LIB.requireReadable();
		Environment.LOG.requireWritable();
		Environment.CFG.requireWritable();
		Environment.PLUGIN.requireWritable();

		return outcome.success();
	});

	/**
	 * Load static stores.
	 */
	@InitializationTask(name = "Load static stores", fatal = true)
	public static final Task loadStores = new Task(outcome -> {

		ThreadStore.init(config -> {
			config.defaults.put("net.exelet", new NioEventLoopGroup(2).next());
			config.defaults.put("net.connection.outgoing", new NioEventLoopGroup(2).next());
			config.defaults.put("net.connection.loop", new NioEventLoopGroup(2).next());
			config.defaults.put("net.message.incoming", new UnorderedThreadPoolEventExecutor(2));
			config.defaults.put("store.event_bus", Executors.newSingleThreadExecutor());
			config.defaults.put("attributes", Executors.newScheduledThreadPool(1));
		});

		STStore.init(config -> {
			config.concurrency = 1;
			config.root = new EphemeralDocument();
		});

		ProfileStore.init(config -> {
			config.collection = STStore.root();
		});

		PluginStore.init(config -> {
			config.collection = STStore.get(InstanceOid().profile(Core.UUID).plugin);
		});

		StreamStore.init(config -> {
		});

		ExeletStore.init(config -> {
			config.exelets = List.of(AgentExe.class);
		});

		ConnectionStore.init(config -> {
			config.collection = STStore.get(InstanceOid().profile(Core.UUID).connection);
		});

		NetworkStore.init(config -> {
		});

		NetworkStore.register(new Object() {
			@Subscribe
			private void onSrvLost(ServerLostEvent event) {
				ConnectionStore.connect(config -> {
					config.address(CfgAgent.SERVER_ADDRESS.value().get());
					config.timeout = CfgAgent.SERVER_TIMEOUT.value().orElse(1000);
					config.bootstrap.handler(new ClientChannelInitializer(struct -> {
						struct.clientTlsInsecure();
					}));
				});
			}

			@Subscribe
			private void onSrvEstablished(ServerEstablishedEvent event) {
				CompletionStage<Outcome> future;

				switch (CfgAgent.AUTH_TYPE.value().orElse("none")) {
				case "password":
					future = AuthCmd.async().target(event.get()).password(CfgAgent.AUTH_PASSWORD.value().get());
					break;
				default:
					future = AuthCmd.async().target(event.get()).none();
					break;
				}

				future = future.thenApply(rs -> {
					if (!rs.getResult()) {
						// Close the connection
						ConnectionStore.getByCvid(event.get()).ifPresent(sock -> {
							sock.close();
						});
					}
					return rs;
				});

				if (CfgInstance.PLUGIN_ENABLED.value().orElse(true)) {
					future.thenAccept(rs -> {
						if (rs.getResult()) {
							// Synchronize plugins
							PluginCmd.async().synchronize().thenRun(PluginStore::loadPlugins);
						}
					});
				}
			}
		});

		return outcome.success();
	});

	@InitializationTask(name = "Load plugins")
	public static final Task loadPlugins = new Task(outcome -> {
		if (!CfgInstance.PLUGIN_ENABLED.value().orElse(true))
			return outcome.skipped();

		PluginStore.scanPluginDirectory();
		PluginStore.loadPlugins();

		return outcome.success();
	});

	@InitializationTask(name = "Begin the connection routine", fatal = true)
	public static final Task beginConnectionRoutine = new Task(outcome -> {
		ConnectionStore.connect(config -> {
			config.address(CfgAgent.SERVER_ADDRESS.value().get());
			config.timeout = CfgAgent.SERVER_TIMEOUT.value().orElse(1000);
			config.bootstrap.handler(new ClientChannelInitializer(struct -> {
				struct.clientTlsInsecure();
			}));
		});

		return outcome.success();
	});

	private Agent() {
	}

	static {
		MainDispatch.register(Agent.class);
	}
}
