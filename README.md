# GoonClansEngine 🏰

**A powerful Minecraft Bukkit/Spigot plugin for managing clans on your server.**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.16%2B-brightgreen)](https://www.minecraft.net/)
[![Java](https://img.shields.io/badge/Java-8%2B-blue)](https://www.java.com/)
[![Version](https://img.shields.io/badge/Version-0.9.5-violet)](https://github.com/Ketsman_Show/GoonClansEngine/releases)

---

## ✨ Overview

GoonClansEngine is an all-in-one plugin designed to bring a rich clan system to your Minecraft server. Manage clans effortlessly with features such as customizable tags, offline invitations, public or private clan modes, and advanced leadership and admin tools. With an integrated quest system, a clan shop for exciting upgrades, and friendly relationship management, your server will never be the same!

---

## 🛠 Features

- **Clan Creation & Management:**  
  - Create clans with custom tags and one-word names.
  - Transfer leadership seamlessly.
  - Invite players (even offline) and manage join requests.
  - Open clans allow instant joining; closed clans require invitations.

- **Clan Logs & Points:**  
  - Detailed clan logs visible to clan leaders.
  - Track and display clan points earned through quests and activities.
  - Admin commands to modify clan points for balance or events.

- **Clan Relationships:**  
  - Set friendly, enemy, or neutral relationships with other clans.
  - Automatic notifications when relationships change.
  - Block PvP between clanmates and allied clans to prevent friendly fire.

- **Quest System:**  
  - **Daily & Weekly Quests:** Complete tasks like mining, crafting, killing mobs, traveling, and more!
  - **Automatic Tracking:** Objectives such as killing specific mobs or traveling are tracked in real-time.
  - **Manual Submission:** For quests requiring items, submit them via command.
  - **Admin Reset:** Operators can reset daily, weekly, or all quests with a single command.

- **Clan Shop & Upgrades:**  
  - Purchase upgrades such as shared chests, double chest capacity, clan home teleport, and increased member limits.
  - Upgrade system available when your clan reaches a minimum number of members.
  - Visual feedback on purchased and available upgrades.

- **Additional Utilities:**  
  - Custom chat formatting with clan tags.
  - PvP protection – teleportation to clan home is disabled immediately after combat.
  - Debug commands for operators to test and adjust clan settings.

---

## 📜 Commands

### General Clan Commands
| Command | Description |
|---------|-------------|
| `/clan create <tag> <name>` | Create a new clan with a custom tag and name. |
| `/clan invite <player>` | Invite a player to your clan (offline invitations supported). |
| `/clan accept <clanName>` | Accept a clan invitation. |
| `/clan decline <clanName>` | Decline a clan invitation. |
| `/clan join <clanName>` | Request to join an open clan. |
| `/clan kick <player>` | Remove a player from your clan (leader only). |
| `/clan leave` | Leave your current clan. |
| `/clan disband` | Disband your clan (leader or op only). |
| `/clan transfer <player>` | Transfer clan leadership to another member. |
| `/clan logs [clanName]` | View clan logs (your own if leader; ops can view any). |
| `/clan points` | Check your clan's current points. |
| `/clan open` | Make your clan public for instant joining. |
| `/clan close` | Set your clan to invite-only. |

### Advanced & Admin Commands
| Command | Description |
|---------|-------------|
| `/clan debug 4 <clanName>` | Enable debug mode (forces clan to have 4+ members) for testing. |
| `/clan debug normal <clanName>` | Disable debug mode. |
| `/clan admin addpoints <clanName> <amount>` | (Operator only) Add points to a clan. |

### Quest System Commands
| Command | Description |
|---------|-------------|
| `/clan quest list` | Display all available daily and weekly quests with their status. |
| `/clan quest complete <questID>` | Manually complete a quest that requires item submission (auto-tracked quests cannot be submitted manually). |
| `clan quest reset`| **(Op only)** Reset quests: daily, weekly, or both. |

### Clan Shop & Home Commands
| Command | Description |
|---------|-------------|
| `/clan shop` | View the clan shop and available upgrades (requires a minimum number of clan members). |
| `/clan shop upgrade` | Display the next available upgrade with its cost. |
| `/clan shop upgrade confirm` | Confirm purchase of the next upgrade. |
| `/clan chest` | Open your clan's shared chest. |
| `/clan sethome` | Set your clan's home location (leader only, requires clan home upgrade). |
| `/clan home` | Teleport to your clan's home (cannot be used immediately after PvP). |

### Clans List & Info
| Command | Description |
|---------|-------------|
| `/clans` | View a list of all clans with status tags and member counts. |
| `/clans info <clanName>` | View detailed information about a specific clan. |
| `/clans help (color)` | Display help information and color formatting guidelines. |

---

## 🚀 Installation

1. **Download the Plugin:**  
   Grab the latest `GoonClansEngine.jar` from the [Releases](https://github.com/Ketsman_Show/GoonClansEngine/releases) page.

2. **Install:**  
   Place the `GoonClansEngine.jar` file into your server’s `plugins/` folder.

3. **Restart or Reload:**  
   Restart your server or run `/reload` to enable the plugin.

4. **Configuration (Optional):**  
   Customize settings in the generated configuration files (if applicable).

5. **Enjoy:**  
   Use `/clans help` in-game to view available commands and start building your clan community!

---

## 💡 Compatibility

- **Minecraft:** 1.16+ (Spigot, Paper, etc.)
- **Java:** 8+
- **Integrations:** Compatible with popular chat, economy, and permission plugins.

---

## 🤝 Contributing

Contributions are welcome! Whether you want to add new features, fix bugs, or improve documentation, feel free to open an issue or submit a pull request. Please follow the repository’s [CONTRIBUTING.md](CONTRIBUTING.md) guidelines.

---

## 📜 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for more details.

---

## 🔗 Author & Community

**Author:** [Ketsman_Show](https://github.com/Ketsman_Show)  
**Official Build:** _Coming Soon_  
[![Discord](https://img.shields.io/badge/Discord-Join%20Us-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/ayin)

---

*Elevate your Minecraft server with a dynamic clan system that encourages teamwork, friendly competition, and epic adventures!*
