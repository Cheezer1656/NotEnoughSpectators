package cheeezer.notenoughspectators.tunnel;

import cheeezer.notenoughspectators.NotEnoughSpectators;
import cheeezer.notenoughspectators.NotEnoughSpectatorsClient;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

public class TunnelClient extends Thread {
    public static final int CONTROL_PORT = 7835;
    private final int localPort;
    private final int remotePort;
    private final Socket socket;
    private final BufferedReader in;

    public TunnelClient(int port) throws Exception {
        super("TunnelClient");

        this.localPort = port;
        socket = new Socket(NotEnoughSpectatorsClient.getConfig().getBoreServerHost(), CONTROL_PORT);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));

        out.print("{\"Hello\":0}\0");
        out.flush();
        String response = read();
        remotePort = JsonParser.parseString(response).getAsJsonObject().get("Hello").getAsInt();
        NotEnoughSpectators.LOGGER.debug("Connected to tunnel server on port {}", remotePort);
    }

    public int getRemotePort() {
        return remotePort;
    }

    public void run() {
        try {
            while (true) {
                String message = read();
                if (message.isEmpty() || Thread.interrupted()) break;
                if (message.equals("\"Heartbeat\"")) continue;
                String uuid = JsonParser.parseString(message).getAsJsonObject().get("Connection").getAsString();
                NotEnoughSpectators.LOGGER.debug("Received connection request with UUID: {}", uuid);
                TunnelChannel channel = new TunnelChannel(localPort, uuid);
                channel.start();
            }
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String read() throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1 && c != 0) {
            sb.append((char) c);
        }
        return sb.toString();
    }
}
