package cheeezer.notenoughspectators.config;

import cheeezer.notenoughspectators.NotEnoughSpectators;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = NotEnoughSpectators.MOD_ID)
public class NESConfigData implements ConfigData {
    int localPort = 25566;
    String boreServerHost = "bore.pub";

    public int getLocalPort() {
        return localPort;
    }

    public String getBoreServerHost() {
        return boreServerHost;
    }
}