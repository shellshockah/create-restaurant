package net.kixin.createrestaurant.network;

import net.kixin.createrestaurant.CreateRestaurant;
import net.kixin.createrestaurant.blockentity.MenuBlockEntity;
import net.kixin.createrestaurant.client.RestaurantMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ActionPayload(BlockPos pos, int action, int row, int value, String text)
        implements CustomPacketPayload {
    public static final int TOGGLE_GAME = 0;
    public static final int SET_NAME = 1;
    public static final int SET_PRICE = 2;
    public static final int ADD_ROW = 3;
    public static final int SUB_ROW = 4;
    public static final int COLLECT_EARNINGS = 5;

    public static final Type<ActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateRestaurant.MODID, "restaurant_action")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ActionPayload decode(RegistryFriendlyByteBuf buffer) {
            return new ActionPayload(
                    buffer.readBlockPos(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readUtf(32)
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ActionPayload payload) {
            buffer.writeBlockPos(payload.pos);
            buffer.writeVarInt(payload.action);
            buffer.writeVarInt(payload.row);
            buffer.writeVarInt(payload.value);
            buffer.writeUtf(payload.text, 32);
        }
    };

    public static ActionPayload simple(BlockPos pos, int action) {
        return new ActionPayload(pos, action, 0, 0, "");
    }

    public static void handle(ActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(player.containerMenu instanceof RestaurantMenu menu)
                || !menu.getBlockPos().equals(payload.pos)
                || player.distanceToSqr(
                payload.pos.getX() + 0.5,
                payload.pos.getY() + 0.5,
                payload.pos.getZ() + 0.5
        ) > 64.0) {
            return;
        }

        MenuBlockEntity blockEntity = menu.getBlockEntity();
        if (blockEntity == null || blockEntity.isRemoved()) {
            return;
        }

        switch (payload.action) {
            case TOGGLE_GAME -> blockEntity.toggleGame(player);
            case SET_NAME -> blockEntity.setRestaurantName(payload.text);
            case SET_PRICE -> blockEntity.setPrice(payload.row, payload.value);
            case ADD_ROW -> blockEntity.addRow();
            case SUB_ROW -> blockEntity.subRow();
            case COLLECT_EARNINGS -> blockEntity.collectEarnings(player);
            default -> { }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}