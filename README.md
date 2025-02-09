# 🏰 GoonClansEngine

<div align="center">
  
![Version](https://img.shields.io/badge/version-0.9.6--PreRelease-blue.svg)
![Spigot](https://img.shields.io/badge/Spigot-1.21.4-orange.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)
[![Discord](https://img.shields.io/badge/Discord-Join%20Us-7289DA.svg)](https://discord.gg/ayin)

</div>

<p align="center">
  <img src="![GCE-0-9-6-09-02-2025](https://github.com/user-attachments/assets/36a43fa8-0ed0-4c02-a4f3-ea909497445e)
" alt="GoonClansEngine Logo" width="200"/>
</p>

> 🚀 A powerful clan plugin with quests and upgrades system for your Minecraft server!

## 📋 Table of Contents

- [✨ Features](#-features)
- [🔧 Installation](#-installation)
- [📝 Commands](#-commands)
- [🎮 Usage](#-usage)
- [⚙️ Configuration](#%EF%B8%8F-configuration)
- [🏆 Quest System](#-quest-system)
- [💎 Clan Upgrades](#-clan-upgrades)
- [🤝 Clan Relations](#-clan-relations)
- [📜 License](#-license)
- [👥 Support](#-support)

## ✨ Features

- 🏰 **Clan Creation**
  - Unique tags with color support
  - Rank system (leader, members)
  - Customizable clan description
  - Invitation and application system

- 📊 **Points System**
  - Earn points through quests
  - Points transfer between clans
  - Use points for upgrades

- 🎯 **Quests**
  - Daily and weekly tasks
  - Automatic progress tracking
  - Various quest types:
    - Resource gathering
    - Mob killing
    - Fishing
    - Villager trading
    - And much more!

- 🔄 **Clan Upgrades**
  - Shared clan chest
  - Increased storage
  - Clan home teleportation
  - Member limit increase

## 🔧 Installation

1. Download the latest plugin version
2. Place the JAR file in your server's `plugins` folder
3. Restart the server
4. Configure settings in `config.yml`

### 📋 Requirements

- Java 17 or higher
- Paper/Spigot 1.21.4
- Recommended: PlaceholderAPI, Vault

## 📝 Commands

### Basic Commands
| Command | Description |
|---------|-----------|
| `/clan create <tag> <name>` | Create a new clan |
| `/clan invite <player>` | Invite a player to clan |
| `/clan join <clan>` | Join a clan |
| `/clan leave` | Leave current clan |
| `/clans` | List all clans |

### Clan Management
| Command | Description |
|---------|-----------|
| `/clan setdesc <description>` | Set clan description |
| `/clan settag <tag>` | Change clan tag |
| `/clan transfer <player>` | Transfer leadership |
| `/clan kick <player>` | Kick a member |

### Quests and Upgrades
| Command | Description |
|---------|-----------|
| `/clan quest list` | View available quests |
| `/clan quest complete <id>` | Complete a quest |
| `/clan shop` | Open upgrades shop |

## 🎮 Usage

### 🏰 Creating a Clan
1. Use `/clan create <tag> <name>`
2. Set description: `/clan setdesc <description>`
3. Invite players: `/clan invite <player>`

### 💎 Upgrades
1. Earn points through quests
2. Open shop: `/clan shop`
3. Purchase clan upgrades

### 🎯 Quests
1. View available quests: `/clan quest list`
2. Complete tasks
3. Receive clan rewards

## ⚙️ Configuration

```yaml
# Configuration example
settings:
  max_members: 6
  quest_reset_time: 86400
  points_multiplier: 1.0
```

## 🏆 Quest System

### Quest Types
- 📅 **Daily Quests**
  - Reset every 24 hours
  - 4 random quests
  - Lower rewards, simple tasks

- 📆 **Weekly Quests**
  - Reset every 7 days
  - 3 random quests
  - Higher rewards, challenging tasks

### Task Categories
- 🗡️ Mob Killing
- ⛏️ Resource Gathering
- 🎣 Fishing
- 🏃‍♂️ Traveling
- 💰 Trading

## 💎 Clan Upgrades

### Available Upgrades
1. 📦 **Shared Chest**
   - Cost: 100 points
   - Access to clan storage

2. 🗄️ **Double Chest**
   - Cost: 100 points
   - Doubles storage capacity

3. 🏠 **Clan Home**
   - Cost: 200 points
   - Set and teleport capability

4. 👥 **Member Slots**
   - Cost: 120 points
   - +2 member slots

## 🤝 Clan Relations

### Relation Types
- 🤝 Friendly
- ⚔️ Enemy
- 😐 Neutral

### Features
- Points transfer between friendly clans
- PvP system with relations
- Cooperative quest completion

## 📜 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## 👥 Support

- 💬 [Discord Server](https://discord.gg/ayin)
- 📧 Email: support@example.com
- 🌐 [Wiki](https://github.com/yourusername/GoonClansEngine/wiki)

---

<div align="center">
  
### Made with ❤️ for the Minecraft Community

[![Discord](https://img.shields.io/discord/YOUR_DISCORD_SERVER_ID?color=7289DA&label=Discord&logo=discord&logoColor=white)](https://discord.gg/ayin)

</div>
