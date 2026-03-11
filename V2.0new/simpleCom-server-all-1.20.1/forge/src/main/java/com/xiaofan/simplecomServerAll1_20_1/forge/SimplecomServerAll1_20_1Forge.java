package com.xiaofan.simplecomServerAll1_20_1.forge;

import com.xiaofan.simplecomServerAll1_20_1.SimpleComServerLogic;
import com.xiaofan.simplecomServerAll1_20_1.SimplecomServerAll1_20_1;
import dev.architectury.platform.forge.EventBuses;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.net.InetSocketAddress;
import java.util.Optional;

@Mod(SimplecomServerAll1_20_1.MOD_ID)
public final class SimplecomServerAll1_20_1Forge {

    private static SimpleComServerLogic serverLogic;

    public SimplecomServerAll1_20_1Forge() {
        EventBuses.registerModEventBus(SimplecomServerAll1_20_1.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        var forgeBus = MinecraftForge.EVENT_BUS;

        forgeBus.addListener((ServerStartingEvent e) -> {
            SimpleComForgeBridge bridge = new SimpleComForgeBridge(e.getServer());
            serverLogic = SimplecomServerAll1_20_1.initServer(bridge);
        });

        forgeBus.addListener((ServerStoppingEvent e) -> {
            if (serverLogic != null) {
                serverLogic.stop();
                serverLogic = null;
            }
        });

        forgeBus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (serverLogic == null || !(e.getEntity() instanceof ServerPlayer player)) return;
            String address = getAddressString(player);
            Optional<String> kick = serverLogic.onPlayerLogin(address);
            if (kick.isPresent()) {
                player.connection.disconnect(Component.literal(kick.get()));
                return;
            }
            serverLogic.onPlayerJoin(player.getName().getString());
        });

        forgeBus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (serverLogic != null && e.getEntity() instanceof ServerPlayer player) {
                serverLogic.onPlayerQuit(player.getName().getString());
            }
        });
    }

    private static String getAddressString(ServerPlayer player) {
        if (player.connection == null || player.connection.getRemoteAddress() == null) return "";
        var addr = player.connection.getRemoteAddress();
        if (addr instanceof InetSocketAddress inet) {
            return inet.getAddress().getHostAddress();
        }
        return addr.toString();
    }
}
