package cheezer.notenoughspectators.command;

import cheezer.notenoughspectators.NotEnoughSpectators;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import java.util.Arrays;
import java.util.List;

public class NotEnoughSpectatorsCommand extends CommandBase {
    @Override
    public String getCommandName() {
        return "notenoughspectators";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "notenoughspectators <start <port>|stop>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) {
            throw new CommandException("Usage: /" + getCommandUsage(sender));
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "start":
                if (args.length < 2) {
                    throw new CommandException("Usage: /" + getCommandUsage(sender));
                }
                try {
                    NotEnoughSpectators.startServer(Integer.parseInt(args[1]));
                    sender.addChatMessage(new ChatComponentText("Server started at port " + args[1]));
                } catch (Exception e) {
                    throw new CommandException("Error: " + e.getMessage());
                }
                break;
            case "stop":
                NotEnoughSpectators.stopServer();
                sender.addChatMessage(new ChatComponentText("Server stopped!"));
                break;
            default:
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
