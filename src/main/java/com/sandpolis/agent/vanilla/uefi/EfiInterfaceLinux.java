package com.sandpolis.agent.vanilla.uefi;

public class EfiInterfaceLinux implements EFI {
	
	@Override
	public boolean isEfiMode() {
		// Check for mounted efivars
		
		
		return false;
	}

}
