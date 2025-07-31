package cheezer.notenoughspectators.server;

import cheezer.notenoughspectators.event.MovementEvent;
import cheezer.notenoughspectators.event.PacketEvent;
import cheezer.notenoughspectators.PacketStore;
import cheezer.notenoughspectators.PlayerTaskQueue;
import cheezer.notenoughspectators.mixin.AccessorS46PacketSetCompressionLevel;
import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NettyCompressionDecoder;
import net.minecraft.network.NettyCompressionEncoder;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.server.S02PacketLoginSuccess;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.*;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;
import net.minecraft.network.status.server.S00PacketServerInfo;
import net.minecraft.network.status.server.S01PacketPong;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Collections;
import java.util.Random;
import java.util.UUID;

import static net.minecraft.network.NetworkManager.attrKeyConnectionState;

public class SpectatorServerNetworkHandler extends SimpleChannelInboundHandler<Packet<?>>  {
    public static final int COMPRESSION_THRESHOLD = 256;
    private static final ItemStack TELEPORT_ITEM = createTeleportItem();
    private final SpectatorServer server;
    private Channel channel;

    public SpectatorServerNetworkHandler(SpectatorServer server) {
        this.server = server;
    }

    @Override
    public void channelActive(ChannelHandlerContext context) throws Exception {
        super.channelActive(context);
        channel = context.channel();
        setConnectionState(EnumConnectionState.HANDSHAKING);
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        super.channelInactive(context);
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, Packet<?> packet) {
        if (channel.isOpen()) {
            EnumConnectionState phase = getNetworkPhase();
            if (phase == EnumConnectionState.HANDSHAKING) {
                if (packet instanceof C00Handshake) {
                    setConnectionState(((C00Handshake) packet).getRequestedState());
                } else {
                    context.disconnect();
                }
            } else if (phase == EnumConnectionState.STATUS) {
                if (packet instanceof C00PacketServerQuery) {
                    channel.writeAndFlush(new S00PacketServerInfo(server.getServerStatus()));
                } else if (packet instanceof C01PacketPing) {
                    channel.writeAndFlush(new S01PacketPong(((C01PacketPing) packet).getClientTime()));
                }
            } else if (phase == EnumConnectionState.LOGIN) {
                if (packet instanceof C00PacketLoginStart) {
                    channel.writeAndFlush(new S02PacketLoginSuccess(new GameProfile(UUID.fromString("41C82C87-7AfB-4024-BA57-13D2C99CAE77"), "[Minecraft]")));
                    setConnectionState(EnumConnectionState.PLAY);

                    S46PacketSetCompressionLevel compressionPacket = new S46PacketSetCompressionLevel();
                    ((AccessorS46PacketSetCompressionLevel) compressionPacket).setThreshold(COMPRESSION_THRESHOLD);
                    channel.writeAndFlush(compressionPacket);
                    setCompressionThreshold();

                    for (Packet<?> packet1 : PacketStore.getPlayPackets()) {
                        channel.writeAndFlush(packet1);
                    }

                    configureClient(Minecraft.getMinecraft().thePlayer, true);

                    MinecraftForge.EVENT_BUS.register(this);

                    new Thread(() -> {
                        Random rand = new Random();
                        while (channel.isOpen()) {
                            try {
                                Thread.sleep(10000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }

                            channel.writeAndFlush(new S00PacketKeepAlive(rand.nextInt()));
                        }
                    }).start();
                }
            } else if (phase == EnumConnectionState.PLAY) {
                if (packet instanceof C08PacketPlayerBlockPlacement) {
                    ItemStack itemStack = ((C08PacketPlayerBlockPlacement) packet).getStack();
                    if (itemStack != null && itemStack.getItem() != TELEPORT_ITEM.getItem()) return;
                    EntityPlayer player = Minecraft.getMinecraft().thePlayer;
                    channel.writeAndFlush(new S08PacketPlayerPosLook(player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch, Collections.emptySet()));
                }
            } else {
                System.out.println("Unexpected packet in phase " + phase + ": " + packet.getClass().getSimpleName());
            }
        }
    }

    @SubscribeEvent
    public void onPacketReceived(PacketEvent event) {
        if (channel.isOpen() && getNetworkPhase() == EnumConnectionState.PLAY) {
            channel.writeAndFlush(event.getPacket());
            if (event.getPacket() instanceof S01PacketJoinGame) {
                PlayerTaskQueue.addTask((player) -> {
                    configureClient(player, false);
                    PlayerTaskQueue.addPositionTask((pos) -> {
                        channel.writeAndFlush(new S08PacketPlayerPosLook(
                                pos.x, pos.y, pos.z, pos.yaw, pos.pitch, Collections.emptySet()));
                        spawnHostPlayer();
                    });
                });
            }
        }
    }

    @SubscribeEvent
    public void onMovementEvent(MovementEvent event) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (channel.isOpen() && getNetworkPhase() == EnumConnectionState.PLAY && player != null) {
            // Modified copy of code from EntityTrackerEntry.updatePlayerList
            int id = player.getEntityId();
            int k = MathHelper.floor_double(player.lastTickPosX * 32.0);
            int j1 = MathHelper.floor_double(player.lastTickPosY * 32.0);
            int k1 = MathHelper.floor_double(player.lastTickPosZ * 32.0);
            int l1 = MathHelper.floor_float(player.prevRotationYaw * 256.0f / 360.0f);
            int i2 = MathHelper.floor_float(player.prevRotationPitch * 256.0f / 360.0f);
            int j2 = k - MathHelper.floor_double(player.posX);
            int k2 = j1 - MathHelper.floor_double(player.posY);
            int i = k1 - MathHelper.floor_double(player.posZ);
            boolean flag = Math.abs(j2) >= 4 || Math.abs(k2) >= 4 || Math.abs(i) >= 4;
            boolean flag1 = Math.abs(l1 - player.rotationYaw) >= 4 || Math.abs(i2 - player.rotationPitch) >= 4;
            if (flag) {
                if (j2 >= -128 && j2 < 128 && k2 >= -128 && k2 < 128 && i >= -128 && i < 128) {
                    channel.writeAndFlush(new S14PacketEntity.S15PacketEntityRelMove(id, (byte)j2, (byte)k2, (byte)i, player.onGround));
                    if (flag1) {
                        channel.writeAndFlush(new S14PacketEntity.S16PacketEntityLook(id, (byte)l1, (byte)i2, player.onGround));
                    }
                } else {
                    channel.writeAndFlush(new S18PacketEntityTeleport(id, k, j1, k1, (byte) l1, (byte) i2, player.onGround));
                }
            }
            if (flag1) channel.writeAndFlush(new S19PacketEntityHeadLook(player, (byte) l1));
        }
    }

    private void configureClient(EntityPlayer player, boolean isFirstJoin) {
        if (player == null || !channel.isOpen()) return;
        // Respawn the client to reload the world
        World world = player.worldObj;
        channel.writeAndFlush(new S07PacketRespawn(2, world.getDifficulty(), world.getWorldType(), world.getWorldInfo().getGameType()));
        channel.writeAndFlush(new S07PacketRespawn(world.provider.getDimensionId(), world.getDifficulty(), world.getWorldType(), world.getWorldInfo().getGameType()));

        if (isFirstJoin) {
            // Teleport the client to the host
            channel.writeAndFlush(new S08PacketPlayerPosLook(player.posX, player.posY, player.posZ, MathHelper.floor_float(player.rotationYaw * 256.0f / 360.0f), MathHelper.floor_float(player.rotationPitch * 256.0f / 360.0f), Collections.emptySet()));
            // Send chunk packets here to fix bug where chunks are loaded but invisible when client has high latency first joins
            for (Packet<?> packet : PacketStore.getChunkPackets()) {
                channel.writeAndFlush(packet);
            }
            // Spawn the host player
            spawnHostPlayer();
        } else {
            // Chunks aren't sent with the other Play packets to prevent redundant chunk loading
            for (Packet<?> packet : PacketStore.getChunkPackets()) {
                channel.writeAndFlush(packet);
            }
        }

        // Give the client a teleport item
        channel.writeAndFlush(new S2FPacketSetSlot(0, 36, TELEPORT_ITEM));

        // Set client to creative mode
        channel.writeAndFlush(new S2BPacketChangeGameState(3, 1F));
    }

    private void spawnHostPlayer() {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        channel.writeAndFlush(new S0CPacketSpawnPlayer(player));
        for (int slot = 0; slot < 5; slot++) {
            ItemStack itemStack = player.getEquipmentInSlot(slot);
            if (itemStack != null)
                channel.writeAndFlush(new S04PacketEntityEquipment(player.getEntityId(), slot, itemStack));
        }
    }

    public static void updateEquipment() {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        for (int slot = 0; slot < 5; slot++) {
            MinecraftForge.EVENT_BUS.post(new PacketEvent(new S04PacketEntityEquipment(player.getEntityId(), slot, player.getEquipmentInSlot(slot))));
        }
    }

    private static ItemStack createTeleportItem() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setTag("display", new NBTTagCompound());
        nbt.getCompoundTag("display").setString("Name", "Teleport to Host");
        ItemStack itemStack = new ItemStack(Items.compass, 1);
        itemStack.setTagCompound(nbt);
        return itemStack;
    }

    private void setConnectionState(EnumConnectionState newState) {
        channel.attr(attrKeyConnectionState).set(newState);
        channel.config().setAutoRead(true);
    }

    private EnumConnectionState getNetworkPhase() {
        return channel.attr(attrKeyConnectionState).get();
    }

    private void setCompressionThreshold() {
        if (this.channel.pipeline().get("decompress") instanceof NettyCompressionDecoder) {
            ((NettyCompressionDecoder)this.channel.pipeline().get("decompress")).setCompressionTreshold(COMPRESSION_THRESHOLD);
        } else {
            this.channel.pipeline().addBefore("decoder", "decompress", new NettyCompressionDecoder(COMPRESSION_THRESHOLD));
        }
        if (this.channel.pipeline().get("compress") instanceof NettyCompressionEncoder) {
            ((NettyCompressionEncoder)this.channel.pipeline().get("decompress")).setCompressionTreshold(COMPRESSION_THRESHOLD);
        } else {
            this.channel.pipeline().addBefore("encoder", "compress", new NettyCompressionEncoder(COMPRESSION_THRESHOLD));
        }
    }
}
