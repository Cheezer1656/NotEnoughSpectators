# NotEnoughUpdates
A Minecraft mod that allows you to invite anybody to watch your gameplay in an immersive way similar to watching as a spectator in the world

## How to use
1. Add this to your Fabric mods folder
2. Start up your game
3. Join any world or server
4. Run the `/nes share <port>` command in the chat
5. Join `localhost:<port>` from another Minecraft instance (Can be on another device, but you'll have to use `<your IP>:<port>`)

**Note:** You can also use the `/nes stop` command to stop sharing your gameplay.

## How does it work?
The host client captures and stores the packets it receives, then broadcasts them to spectator clients when they join, using a dummy Minecraft server.
