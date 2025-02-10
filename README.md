# 🏰 GoonClansEngine

<div align="center">
  [![Version](https://img.shields.io/badge/version-0.9.7--CheckPoint-blue.svg)](https://github.com/yourusername/GoonClansEngine)
  [![Spigot](https://img.shields.io/badge/Spigot-1.21.4-orange.svg)](https://www.spigotmc.org/)
  [![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
  [![Discord](https://img.shields.io/badge/Discord-Join%20Us-7289DA.svg)](https://discord.gg/ayin)
</div>

<p align="center">
  <img src="https://i.imgur.com/025nUWh.png" alt="GoonClansEngine Logo" width="500"/>
</p>

> **GoonClansEngine** is a powerful and feature-rich Minecraft plugin designed for creating, managing, and enhancing clans. Build your clan, complete challenging quests, upgrade your clan facilities, and enjoy unique interactive features while engaging in intense, cooperative gameplay!

## Table of Contents

- [Features](#features)
- [Installation](#installation)
- [Configuration](#configuration)
- [Commands](#commands)
- [Quest System](#quest-system)
- [Clan Upgrades & Buffs](#clan-upgrades--buffs)
- [Clan Relations](#clan-relations)
- [Usage](#usage)
- [Support](#support)
- [License](#license)

## Features

- **Clan Creation & Management**
  - Unique clan tags (with color support) and customizable clan names.
  - Set and update clan descriptions.
  - Invitation/application system with built-in cooldowns.
  - Automatic chat formatting displaying clan tags.

- **Points & Progression System**
  - Earn points by completing diverse quests.
  - Use points to purchase clan upgrades and buffs.
  - Detailed clan log system to record events and progress.

- **Quest System**
  - **Daily and Weekly Quests:** Engage in various tasks ranging from traveling and mining to mob slaying and leveling up.
  - **Interactive Notifications:** When quest conditions are met, players receive a clickable chat message with a **[Complete]** button that executes `/clan quest complete <questID>`.
  - **5-Minute Cooldown:** To avoid chat spam, each quest notification is limited to once every 5 minutes per player, with individual cooldown timers per quest.
  - **Auto-Completion & Progress Tracking:** Seamlessly track progress for objectives like travel distance, block breaking, mob kills, and level changes.

- **Clan Upgrades & Buffs**
  - **Shared Clan Chest:** Secure storage accessible to all clan members.
  - **Double Chest Capacity:** Expand your shared chest to store more items.
  - **Clan Home Teleport:** Set and teleport to a clan home for easy meeting points.
  - **Increase Member Limit:** Boost your clan's capacity by unlocking additional member slots.
  - **Buff System:** Purchase powerful buffs that automatically apply beneficial potion effects to all online clan members.

- **Clan Relations**
  - Set clan relationships as Friendly, Neutral, or Enemy.
  - Enable cooperation or competition with features like points transfer and PvP incentives based on relations.

## Installation

1. **Download Plugin:**
   - Get the latest release of GoonClansEngine from the [Releases](https://github.com/yourusername/GoonClansEngine/releases) page.

2. **Setup:**
   - Place the JAR file in your server's `plugins` folder.
   - Restart your Minecraft server to load the plugin.

3. **Requirements:**
   - Java 17 or higher.
   - Paper/Spigot 1.21.4.
   - Recommended: PlaceholderAPI, Vault for enhanced functionality.

## Configuration

GoonClansEngine comes with an extensive configuration file (`config.yml`) allowing you to fine-tune various aspects of the plugin. Customize settings such as:

- Quest reset timers (daily/weekly)
- Points multipliers
- Buff durations and costs
- Notification cooldown timings

Example configuration snippet:
```yaml
settings:
  max_members: 6
  quest_reset_time: 86400   # Reset quests every 24 hours (in seconds)
  points_multiplier: 1.0
  notification_cooldown: 300000  # 5 minutes in milliseconds
```

## Commands

### Basic Commands
| Command                            | Description                                                  |
|------------------------------------|--------------------------------------------------------------|
| `/clan create <tag> <name>`        | Create a new clan with the specified tag and name.           |
| `/clan invite <player>`            | Invite a player to join your clan.                           |
| `/clan join <clan>`                | Request to join an existing clan.                            |
| `/clan leave`                      | Leave your current clan.                                     |
| `/clans`                         | List all available clans.                                    |

### Clan Management
| Command                            | Description                                                  |
|------------------------------------|--------------------------------------------------------------|
| `/clan setdesc <description>`      | Set or update your clan's description.                       |
| `/clan settag <tag>`               | Change your clan's tag.                                      |
| `/clan transfer <player>`          | Transfer clan leadership to another member.                  |
| `/clan kick <player>`              | Remove a member from your clan.                              |

### Quest & Upgrade Commands
| Command                                   | Description                                                                                   |
|-------------------------------------------|-----------------------------------------------------------------------------------------------|
| `/clan quest list`                        | Display available daily and weekly quests.                                                     |
| `/clan quest complete <questID>`          | Complete a quest (can also be done via the interactive **[Complete]** button).                   |
| `/clan shop`                              | Open the shop to purchase clan upgrades and buffs.                                             |

## Quest System

The quest system is at the heart of GoonClansEngine. It keeps your clan active and engaged through various challenges:

- **Multiple Quest Types:**
  - **Travel:** Track the distance players move (measured in blocks).
  - **Mine:** Monitor progress through block breaking activities.
  - **Kill:** Engage in combat with specific mobs (e.g., Wither Skeletons, Ender Dragon).
  - **Level:** Advance player levels to meet target criteria.

- **Interactive Notifications:**
  - When a quest is ready to be completed, players receive a green chat notification with a **[Complete]** button that, when clicked, executes the command `/clan quest complete <questID>`.

- **5-Minute Per-Quest Cooldown:**
  - To prevent notification spam, each quest's completion message is limited to once every 5 minutes per player. Each quest maintains its own cooldown timer, so different quests can notify independently.

- **Progress Tracking & Auto-Completion:**
  - The plugin continuously monitors player actions (such as moving, mining, killing mobs, and leveling up) and updates quest progress automatically. Some quests complete automatically once their requirements are met.

## Clan Upgrades & Buffs

Enhance your clan's capabilities by investing points earned from quests into valuable upgrades:

- **Shared Clan Chest:**  
  Unlock a centrally managed inventory for storing and sharing items among clan members.

- **Double Chest Capacity:**  
  Increase the size of your shared chest to accommodate more resources.

- **Clan Home Teleport:**  
  Purchase the ability to set a clan home, making it easier for members to regroup quickly.

- **Increase Member Limit:**  
  Expand your clan's maximum member count by unlocking additional slots.

- **Buff System:**  
  Buy clan buffs that give temporary, beneficial potion effects to all online clan members. These buffs can dramatically enhance performance in battles, quests, or general gameplay.

## Clan Relations

Forge alliances or rivalries with other clans to enrich your gameplay experience:

- **Relationship Status:**
  - Assign each clan a relationship status: Friendly, Neutral, or Enemy.
  
- **Collaborative Features:**
  - Friendly clans can transfer points, cooperate during quests, and support each other in PvP situations.
  
- **Competitive Elements:**
  - Engage in clan-versus-clan battles and competitions based on predefined relations.

## Usage

1. **Creating & Managing Your Clan:**
   - Start by creating your clan with `/clan create <tag> <name>`.
   - Use various management commands to update your clan's description, invite new members, or adjust your clan tag.

2. **Earning & Spending Points:**
   - Complete daily and weekly quests to earn valuable points.
   - Invest points within the clan shop to unlock upgrades and purchase powerful buffs.

3. **Participate in Quests:**
   - Use `/clan quest list` to view all available quests.
   - When a quest's conditions are met, simply click the interactive **[Complete]** button in the chat or execute the complete command manually.

4. **Collaborative Gameplay:**
   - Work with your clan members to tackle challenging objectives, accumulate points, and enjoy the benefits of upgraded clan facilities.

## Support

If you encounter any issues or have any questions regarding GoonClansEngine, please use the following support channels:

- **Discord Server:** [Join our Discord](https://discord.gg/ayin)
- **Wiki:** [GoonClansEngine Wiki](https://github.com/yourusername/GoonClansEngine/wiki)
- **Email:** support@example.com

## License

GoonClansEngine is available under the [MIT License](LICENSE).

---

<div align="center">
  <span>Made with ❤️ for the Minecraft Community</span>
</div> 
