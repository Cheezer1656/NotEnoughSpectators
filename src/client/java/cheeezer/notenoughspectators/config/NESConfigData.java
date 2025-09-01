package cheeezer.notenoughspectators.config;

import cheeezer.notenoughspectators.NotEnoughSpectators;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = NotEnoughSpectators.MOD_ID)
public class NESConfigData implements ConfigData {
    boolean shouldAnnounceJoins = true;
    int localPort = 25566;
    boolean shouldTunnel = true;
    String boreServerHost = "bore.pub";

    public boolean shouldAnnounceJoins() {
        return shouldAnnounceJoins;
    }

    public int getLocalPort() {
        return localPort;
    }

    public boolean shouldTunnel() {
        return shouldTunnel;
    }

    public String getBoreServerHost() {
        return boreServerHost;
    }
}