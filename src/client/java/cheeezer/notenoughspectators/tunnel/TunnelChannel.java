package cheeezer.notenoughspectators.tunnel;

import cheeezer.notenoughspectators.NotEnoughSpectatorsClient;

import java.net.Socket;
import java.net.SocketException;

public class TunnelChannel extends Thread {
    private final int localPort;
    private final String uuid;

    public TunnelChannel(int localPort, String uuid) {
        super("TunnelChannel-" + uuid);
        setDaemon(true);

        this.localPort = localPort;
        this.uuid = uuid;
    }

    public void run() {
        try {
            Socket localSocket = new Socket("localhost", localPort);
            Socket remoteSocket = new Socket(NotEnoughSpectatorsClient.getConfig().getBoreServerHost(), TunnelClient.CONTROL_PORT);

            remoteSocket.getOutputStream().write(("{\"Accept\":\"" + uuid + "\"}\0").getBytes());

            Thread t1 = new Thread(() -> {
                try {
                    forward(localSocket, remoteSocket);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            Thread t2 = new Thread(() -> {
                try {
                    forward(remoteSocket, localSocket);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            t1.start();
            t2.start();
            t1.join();
            t2.join();
            localSocket.close();
            remoteSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void forward(Socket from, Socket to) throws Exception {
        byte[] buffer = new byte[1024];
        int bytesRead;

        while ((bytesRead = from.getInputStream().read(buffer)) != -1) {
            try {
                to.getOutputStream().write(buffer, 0, bytesRead);
                to.getOutputStream().flush();
            } catch (SocketException ignored) {
                break;
            }
        }
    }
}
