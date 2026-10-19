package dev.ahrivaea.map;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Tells players' games that this server has Ahrivaea Map: a server with the mod accepts this (empty) message, so the
 * channel is listed when a player joins. A player's copy of the mod only switches on its own client-side parts when
 * the server they're on has the mod too, so having it in a modpack does nothing on servers without it.
 */
public record Present() implements CustomPacketPayload {
	public static final Type<Present> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ahrivaea_map", "present"));
	public static final StreamCodec<RegistryFriendlyByteBuf, Present> CODEC = StreamCodec.unit(new Present());

	@Override
	public Type<Present> type() {
		return TYPE;
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {});
	}
}
