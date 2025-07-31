package cheezer.notenoughspectators.command;

import cheezer.notenoughspectators.NotEnoughSpectators;
import cheezer.notenoughspectators.tunnel.TunnelClient;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import java.util.Arrays;
import java.util.List;

public class NotEnoughSpectatorsCommand extends CommandBase {
    @Override
    public String getCommandName() {
        return "notenoughspectators";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "notenoughspectators <share [port]|stop>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) {
            throw new CommandException("Usage: /" + getCommandUsage(sender));
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "share":
                new Thread(() -> {
                    try {
                        int localPort;
                        if (args.length < 2) {
                            localPort = 25566; // Default port
                        } else {
                            localPort = Integer.parseInt(args[1]);
                            if (localPort < 1024 || localPort > 65535) {
                                throw new CommandException("Port must be between 1024 and 65535");
                            }
                        }
                        NotEnoughSpectators.startServer(localPort);
                        int port = NotEnoughSpectators.startTunnelClient(localPort);
                        String address = String.format("%s:%d", TunnelClient.REMOTE_HOST, port);
                        sender.addChatMessage(new ChatComponentText("Server started! Join at ").appendSibling(new ChatComponentText(address).setChatStyle(new ChatStyle().setUnderlined(true).setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText("Click to put in chat"))).setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, address)))));
                    } catch (Exception e) {
                        sender.addChatMessage(new ChatComponentText("Error: " + e.getMessage()).setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
                    }
                }).start();
                break;
            case "stop":
                NotEnoughSpectators.stopServer();
                NotEnoughSpectators.stopTunnelClient();
                sender.addChatMessage(new ChatComponentText("Server stopped!"));
                break;
            default:
                throw new CommandException("Usage: /" + getCommandUsage(sender));
        }
    }


    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("nes");
    }
}
