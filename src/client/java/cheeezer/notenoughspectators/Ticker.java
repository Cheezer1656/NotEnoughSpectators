package cheeezer.notenoughspectators;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;

import java.util.Iterator;
import java.util.List;

import static cheeezer.notenoughspectators.NotEnoughSpectators.LOGGER;

public class Ticker extends Thread {
    private final List<ClientConnection> connections;

    public Ticker(List<ClientConnection> connections) {
        super("Spectator Server Ticker");
        this.connections = connections;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                System.err.println("Ticker thread interrupted, stopping...");
                Thread.currentThread().interrupt(); // Restore interrupted status
                break; // Exit the loop if interrupted
            } catch (Exception e) {
                e.printStackTrace();
            }

            for (ClientConnection connection : connections) {
                if (connection.isOpen()) {
                    tick();
                }
            }
        }
    }

    private void tick() {
        synchronized (this.connections) {
            Iterator<ClientConnection> iterator = this.connections.iterator();

            while (iterator.hasNext()) {
                ClientConnection clientConnection = (ClientConnection)iterator.next();
                if (!clientConnection.isChannelAbsent()) {
                    if (clientConnection.isOpen()) {
                        try {
                            clientConnection.tick();
                        } catch (Exception var7) {
                            if (clientConnection.isLocal()) {
                                throw new CrashException(CrashReport.create(var7, "Ticking memory connection"));
                            }

                            LOGGER.warn("Failed to handle packet for {}", clientConnection.getAddressAsString(false), var7);
                            Text text = Text.literal("Internal server error");
                            clientConnection.send(new DisconnectS2CPacket(text), PacketCallbacks.always(() -> clientConnection.disconnect(text)));
                            clientConnection.tryDisableAutoRead();
                        }
                    } else {
                        iterator.remove();
                        clientConnection.handleDisconnection();
                    }
                }
            }
        }
    }
}
