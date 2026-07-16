package com.example.ipv6lanapi.mixin;

import com.example.ipv6lanapi.IPv6LanApiMod;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {

    @Inject(method = "publishToLAN", at = @At("RETURN"))
    private void ipv6lanapi$onPublishToLAN(
            GameType gameType,
            boolean cheats,
            int port,
            CallbackInfoReturnable<Component> cir
    ) {
        IPv6LanApiMod.onLanOpened(port);
    }
}