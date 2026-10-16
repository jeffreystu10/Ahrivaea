# Ahrivaea Gateway (portals between the two servers)

The main world and Ahrivaea are two separate servers. The Gateway is a **datapack** (`datapacks/ahrivaea_gateway`,
released as `ahrivaea-gateway-datapack.zip`) that goes on **both** of them. Walking into a portal shows where it
leads with a clickable **[ Travel there ]**; clicking it moves the player to the other server with Minecraft's own
`/transfer` command. Datapacks are part of vanilla Minecraft, so the main server stays fully vanilla.

Each server keeps its own inventories, XP and spawn points. A player who goes to Ahrivaea and comes back finds their
main-world things where they left them, standing at the portal they left from.

## Setting it up

### 1. On both servers: two settings

In each server's `server.properties` (Hostinger: File Manager, the server's root folder), set:

```
accepts-transfers=true
function-permission-level=3
```

`accepts-transfers` lets players arrive by portal. `function-permission-level=3` lets the datapack use `/transfer`,
which Minecraft counts as an admin command (datapacks normally run one level below that). Restart both servers.

### 2. On both servers: add the datapack

Upload `ahrivaea-gateway-datapack.zip` (from the "latest" release on GitHub) into the world's `datapacks` folder,
`world/datapacks/`, on each server. Restart, or run `/reload`. `/datapack list` should show `file/ahrivaea-gateway-datapack.zip`.

### 3. On the main (vanilla) server

As an operator, in game:

```
/function gateway:setup {name:"Ahrivaea",host:"AHRIVAEA_ADDRESS",port:AHRIVAEA_PORT,note:"You need the Ahrivaea modpack to play there."}
```

Use the Ahrivaea server's address and port as players would type them to join it (Hostinger shows both on the
server's page; a domain name works too). Then stand where you want the portal, facing the way players will walk in,
and run:

```
/function gateway:build
```

It builds a crying obsidian frame with a glowing 3 by 4 opening, 3 blocks in front of you, facing you.

### 4. On the Ahrivaea server

```
/function gateway:setup {name:"the main world",host:"MAIN_ADDRESS",port:MAIN_PORT,note:""}
/function gateway:build
```

Put this return portal where new arrivals should start, and run `/setworldspawn` just in front of it so players
arriving for the first time land beside it.

## Other commands (operators)

| Command | What it does |
|---|---|
| `/function gateway:build` | Builds a portal 3 blocks in front of you, facing you. |
| `/function gateway:place` | Makes your own build a portal: stand in the bottom middle of a 3 wide, 4 tall opening, facing through it. |
| `/function gateway:remove` | Switches off the nearest portal (within 6 blocks). The blocks stay. |
| `/function gateway:setup {...}` | Sets where this server's portals lead. Run it again to change it. |

You can have as many portals as you like; they all lead to the server set with `setup`.

## For players

Ahrivaea needs the modpack: Fabric Loader, Fabric API and the Ahrivaea mods. The modpack can also join the vanilla
server, so players can simply always play with it. A player without the modpack who uses the portal is turned away
by the Ahrivaea server and has to rejoin the main server from the server list.

There are no portal messages for the first 5 seconds after joining, since players usually arrive standing in a portal.
If a trip doesn't go through, the player is told an operator needs to check the two `server.properties` settings above.

## Tests

The server test (CI) loads the datapack on a server, sets it up, builds a portal and checks its blocks and marker
across a restart. The client test `GatewayTest` installs it in a world, builds a portal with a real player, checks
that stepping in offers the trip, that asking from outside a portal is refused, and that `remove` works.
Screenshots: `gateway_1_portal.png`, `gateway_2_offer.png`.
