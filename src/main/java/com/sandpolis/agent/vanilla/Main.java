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

import com.sandpolis.core.agent.init.AgentConnectionRoutine;
import com.sandpolis.core.agent.init.AgentLoadConfiguration;
import com.sandpolis.core.agent.init.AgentLoadStores;
import com.sandpolis.core.instance.Entrypoint;
import com.sandpolis.core.instance.Metatypes.InstanceFlavor;
import com.sandpolis.core.instance.Metatypes.InstanceType;
import com.sandpolis.core.instance.init.InstanceLoadEnvironment;
import com.sandpolis.core.instance.init.InstanceLoadPlugins;

public final class Main extends Entrypoint {

	private Main(String[] args) {
		super(Main.class, InstanceType.AGENT, InstanceFlavor.VANILLA);

		register(new InstanceLoadEnvironment());
		register(new AgentLoadConfiguration());
		register(new AgentLoadStores());
		register(new InstanceLoadPlugins());
		register(new AgentConnectionRoutine());

		start("Sandpolis Agent", args);
	}

	public static void main(String[] args) {
		new Main(args);
	}

}
