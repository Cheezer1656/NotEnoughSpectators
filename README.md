# NotEnoughSpectators
A Minecraft mod that allows you to invite anybody to watch your gameplay in an immersive way similar to watching as a spectator in the world

**Demo Video:**

[![Demo Video](https://img.youtube.com/vi/GzsgztWwvxc/0.jpg)](https://www.youtube.com/watch?v=GzsgztWwvxc)

## How to use
1. Add this to your Fabric mods folder
2. Start up your game
3. Join any world or server
4. Run the `/nes share` command in the chat
5. Get other players to join the server address given by the command (The link is to a Minecraft server, not a webpage. Join using a Minecraft client)

**Note:** You can also use the `/nes stop` command to stop sharing your gameplay.

## How does it work?
The host client captures and stores the packets it receives, then broadcasts them to spectator clients when they join, using a dummy Minecraft server.
