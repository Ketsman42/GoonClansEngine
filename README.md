# GoonClansEngine

GoonClansEngine is a Minecraft server plugin (for Bukkit/Spigot/Paper) that lets players create and manage clans. The plugin offers a wide range of social features—from clan creation and invitations to a clan shop for upgrades.

## Features

- **Clan Creation & Management:**  
  Create clans, invite players, accept invitations, and handle join requests.
- **Member Management:**  
  Kick players, transfer leadership, and leave clans.
- **Clan Customization:**  
  Set unique tags and descriptions for your clan.
- **Clan Shop:**  
  Purchase upgrades such as a shared chest, increased chest capacity, home teleportation upgrade, and increased member limit.
- **Inter-Clan Relationships:**  
  Set relationships (friend, enemy, neutral) with other clans via friendship requests (leaders cannot change the relationship with their own clan).
- **Chat Integration:**  
  Display clan tags alongside player names in the player list and chat.
- **Open Source:**  
  This project is now open source—feel free to modify the plugin or help improve it!

## Commands

### Main Command: `/clan`
- **`/clan create <tag> <name>`** – Create a new clan.
- **`/clan invite <player>`** – Invite a player to your clan.
- **`/clan accept <clanName>`** – Accept an invitation to a clan.
- **`/clan join <clanName>`** – Send a join request to a clan.
- **`/clan kick <player>`** – Kick a player from your clan.
- **`/clan leave`** – Leave your clan.
- **`/clan disband`** – Disband your clan (for leaders or ops only).
- **`/clan delete <clanName>`** – Delete any clan (ops only).
- **`/clan transfer <player>`** – Transfer leadership to another member.
- **`/clan setdesc <description>`** – Set or change your clan’s description.
- **`/clan settag <newTag>`** – Change your clan’s tag.
- **`/clan rename <newName>`** – Rename your clan.
- **`/clan logs [clanName]`** – View clan logs (leaders see their own; ops can view any).
- **`/clan points`** – View your clan’s points.
- **`/clan admin addpoints <clanName> <amount>`** – Add points to a clan (ops only).
- **`/clan setrelation <otherClanName> <friend|enemy|neutral>`** – Set the relationship with another clan. *(Note: Leaders cannot set relationships with their own clan.)*
- **`/clan friendaccept <fromClanName>`** – Accept a friendship request from another clan.
- **`/clan shop`** – View the clan shop.
- **`/clan shop buy <rewardNumber>`** – Purchase an upgrade from the clan shop.
- **`/clan chest`** – Open the clan's shared chest.

### Additional Command: `/clans`
- **`/clans`** – List all clans.
- **`/clans info <clanName>`** – Get detailed information about a specific clan.
- **`/clans help`** – Display help information for clan commands.

## Installation

1. **Download the Plugin:**  
   Download the compiled JAR file.
2. **Installation:**  
   Place the JAR file into your server's `plugins` folder.
3. **Reload/Restart:**  
   Reload your server or execute `/reload`.
4. **Configuration:**  
   On first run, the plugin will generate a `clans.yml` file in its folder for further configuration if needed.

## Contributing

This project is now **open source**. If you want to modify the plugin or help with bug fixes and new features, feel free to fork the repository and contribute your changes. Any help or suggestions are welcome!

Contact:  
Discord: `ketsman.`

## License

This project is licensed under the [MIT License](LICENSE).

---

Enjoy the plugin and happy clanning!
