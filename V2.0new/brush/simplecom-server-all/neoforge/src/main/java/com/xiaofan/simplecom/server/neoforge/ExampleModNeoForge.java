package com.xiaofan.simplecom.server.neoforge;

import net.neoforged.fml.common.Mod;

import com.xiaofan.simplecom.server.ExampleMod;

@Mod(ExampleMod.MOD_ID)
public final class ExampleModNeoForge {
    public ExampleModNeoForge() {
        // Run our common setup.
        ExampleMod.init();
    }
}
