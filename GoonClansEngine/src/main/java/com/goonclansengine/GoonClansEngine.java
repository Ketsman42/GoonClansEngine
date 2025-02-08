package com.yourdomain.goonclansengine;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class GoonClansEngine extends JavaPlugin implements Listener, TabCompleter {

    // Константы
    public static final int MAX_TAG_LENGTH = 16;
    public static final int MAX_CLAN_NAME_LENGTH = 20;
    public static final long INVITE_COOLDOWN_MS = 10000;
    public static final long JOIN_REQUEST_COOLDOWN_MS = 10000;
    public static final long PVP_PROTECTION_MS = 10000;

    // Основные данные плагина
    private final Map<String, Clan> clans = new HashMap<>();
    private File clansFile;
    private FileConfiguration clansConfig;
    private final Map<UUID, Long> inviteCooldown = new HashMap<>();
    private final Map<UUID, Long> joinRequestCooldown = new HashMap<>();

    // Pending friend requests
    private static final Map<String, List<PendingRelationRequest>> pendingFriendRequests = new HashMap<>();

    // Клановый дом и время последнего боя (для телепорта)
    private final Map<String, Location> clanHomes = new HashMap<>();
    private final Map<UUID, Long> lastCombat = new HashMap<>();

    // --- Система квестов ---
    // Сброс квестов: ежедневные – каждые 24 часов, еженедельные – каждые 7 дней
    private long dailyResetTime;
    private long weeklyResetTime;
    // Для каждого клана храним список выполненных квестов (по ID)
    private final Map<String, Set<Integer>> clanDailyQuestsCompleted = new HashMap<>();
    private final Map<String, Set<Integer>> clanWeeklyQuestsCompleted = new HashMap<>();
    // Для автоматических квестов (например, убийство мобов, путешествия) отслеживаем прогресс по ID
    private final Map<String, Map<Integer, Integer>> clanObjectiveProgress = new HashMap<>();

    public static enum QuestType { DAILY, WEEKLY }

    public static enum ObjectiveType { NONE, KILL, TRAVEL }

    public static class Quest {
        public int id;
        public String description;
        public int reward;
        public QuestType type;
        // Если квест требует предметов – используем requirements (Material -> количество)
        public Map<Material, Integer> requirements;
        // Для автоматических квестов
        public ObjectiveType objective = ObjectiveType.NONE;
        public int targetCount = 0;
        public EntityType targetEntity = null; // для квестов KILL

        public Quest(int id, String description, int reward, QuestType type) {
            this.id = id;
            this.description = description;
            this.reward = reward;
            this.type = type;
            this.requirements = new HashMap<>();
        }
        public Quest(int id, String description, int reward, QuestType type, Map<Material, Integer> requirements) {
            this.id = id;
            this.description = description;
            this.reward = reward;
            this.type = type;
            this.requirements = requirements;
        }
    }

    // Вспомогательные методы для создания требований к квестам
    private static Map<Material, Integer> createReq(Material material, int count) {
        Map<Material, Integer> req = new HashMap<>();
        req.put(material, count);
        return req;
    }

    private static Map<Material, Integer> createArmorReq() {
        Map<Material, Integer> req = new HashMap<>();
        req.put(Material.IRON_HELMET, 1);
        req.put(Material.IRON_CHESTPLATE, 1);
        req.put(Material.IRON_LEGGINGS, 1);
        req.put(Material.IRON_BOOTS, 1);
        return req;
    }

    // Инициализация ежедневных квестов
    private static final List<Quest> dailyQuests;
    static {
        List<Quest> d = new ArrayList<>();
        d.add(new Quest(1, "Collect 64 oak logs", 5, QuestType.DAILY, createReq(Material.OAK_LOG, 64)));
        d.add(new Quest(2, "Mine 32 cobblestone", 5, QuestType.DAILY, createReq(Material.COBBLESTONE, 32)));
        Quest q3 = new Quest(3, "Kill 5 zombies", 7, QuestType.DAILY);
        q3.objective = ObjectiveType.KILL;
        q3.targetCount = 5;
        q3.targetEntity = EntityType.ZOMBIE;
        d.add(q3);
        d.add(new Quest(4, "Smelt 16 iron ingots", 6, QuestType.DAILY, createReq(Material.IRON_INGOT, 16)));
        d.add(new Quest(5, "Craft 10 torches", 4, QuestType.DAILY, createReq(Material.TORCH, 10)));
        d.add(new Quest(6, "Farm 10 carrots", 4, QuestType.DAILY, createReq(Material.CARROT, 10)));
        d.add(new Quest(7, "Break 50 blocks", 3, QuestType.DAILY));
        Quest q8 = new Quest(8, "Travel 100 blocks", 5, QuestType.DAILY);
        q8.objective = ObjectiveType.TRAVEL;
        q8.targetCount = 100;
        d.add(q8);
        d.add(new Quest(9, "Plant 20 oak saplings", 4, QuestType.DAILY, createReq(Material.OAK_SAPLING, 20)));
        d.add(new Quest(10, "Trade with a villager", 8, QuestType.DAILY));
        d.add(new Quest(11, "Harvest 10 wheat", 4, QuestType.DAILY, createReq(Material.WHEAT, 10)));
        // Заменено: COOKED_POTATO -> BAKED_POTATO
        d.add(new Quest(12, "Cook 5 potatoes", 4, QuestType.DAILY, createReq(Material.BAKED_POTATO, 5)));
        Quest q13 = new Quest(13, "Kill 3 creepers", 8, QuestType.DAILY);
        q13.objective = ObjectiveType.KILL;
        q13.targetCount = 3;
        q13.targetEntity = EntityType.CREEPER;
        d.add(q13);
        d.add(new Quest(14, "Fish for 3 fish", 5, QuestType.DAILY));
        d.add(new Quest(15, "Craft a stone sword", 4, QuestType.DAILY, createReq(Material.STONE_SWORD, 1)));
        d.add(new Quest(16, "Collect 16 diamonds", 10, QuestType.DAILY, createReq(Material.DIAMOND, 16)));
        d.add(new Quest(17, "Mine 50 iron ore", 8, QuestType.DAILY, createReq(Material.IRON_ORE, 50)));
        d.add(new Quest(18, "Craft 5 golden apples", 12, QuestType.DAILY, createReq(Material.GOLDEN_APPLE, 5)));
        d.add(new Quest(19, "Smelt 32 gold ingots", 10, QuestType.DAILY, createReq(Material.GOLD_INGOT, 32)));
        d.add(new Quest(20, "Build a Nether portal", 15, QuestType.DAILY, createReq(Material.OBSIDIAN, 10)));
        // Дополнительные ежедневные квесты
        d.add(new Quest(21, "Kill 10 spiders", 8, QuestType.DAILY) {{
            objective = ObjectiveType.KILL; targetCount = 10; targetEntity = EntityType.SPIDER;
        }});
        d.add(new Quest(22, "Travel 200 blocks", 8, QuestType.DAILY) {{
            objective = ObjectiveType.TRAVEL; targetCount = 200;
        }});
        d.add(new Quest(23, "Craft 5 furnaces", 6, QuestType.DAILY, createReq(Material.FURNACE, 5)));
        d.add(new Quest(24, "Smelt 20 gold ores", 7, QuestType.DAILY, createReq(Material.GOLD_INGOT, 20)));
        d.add(new Quest(25, "Collect 32 apples", 8, QuestType.DAILY, createReq(Material.APPLE, 32)));
        dailyQuests = d;
    }

    // Инициализация еженедельных квестов
    private static final List<Quest> weeklyQuests;
    static {
        List<Quest> w = new ArrayList<>();
        w.add(new Quest(101, "Resurrect and defeat the Ender Dragon", 100, QuestType.WEEKLY));
        w.add(new Quest(102, "Collect 100 Nether wart in the Nether", 60, QuestType.WEEKLY, createReq(Material.NETHER_WART, 100)));
        w.add(new Quest(103, "Obtain a diamond pickaxe", 50, QuestType.WEEKLY, createReq(Material.DIAMOND_PICKAXE, 1)));
        w.add(new Quest(104, "Craft a full set of iron armor", 40, QuestType.WEEKLY, createArmorReq()));
        w.add(new Quest(105, "Tame 3 horses", 30, QuestType.WEEKLY));
        w.add(new Quest(106, "Build a Nether portal and enter the Nether", 20, QuestType.WEEKLY, createReq(Material.OBSIDIAN, 10)));
        w.add(new Quest(107, "Explore a cave and collect 16 lapis lazuli", 25, QuestType.WEEKLY, createReq(Material.LAPIS_LAZULI, 16)));
        w.add(new Quest(108, "Find and loot a dungeon chest", 30, QuestType.WEEKLY));
        w.add(new Quest(109, "Smelt 100 ores", 35, QuestType.WEEKLY));
        w.add(new Quest(110, "Craft 64 bread", 30, QuestType.WEEKLY, createReq(Material.BREAD, 64)));
        Quest q111 = new Quest(111, "Slay 20 skeletons", 40, QuestType.WEEKLY);
        q111.objective = ObjectiveType.KILL;
        q111.targetCount = 20;
        q111.targetEntity = EntityType.SKELETON;
        w.add(q111);
        w.add(new Quest(112, "Harvest 200 wheat", 35, QuestType.WEEKLY, createReq(Material.WHEAT, 200)));
        w.add(new Quest(113, "Mine 10 diamonds", 50, QuestType.WEEKLY, createReq(Material.DIAMOND, 10)));
        w.add(new Quest(114, "Defeat the Wither", 120, QuestType.WEEKLY));
        w.add(new Quest(115, "Build a beacon pyramid", 70, QuestType.WEEKLY, createReq(Material.IRON_BLOCK, 100)));
        w.add(new Quest(116, "Collect 64 emeralds", 80, QuestType.WEEKLY, createReq(Material.EMERALD, 64)));
        w.add(new Quest(117, "Craft an enchanted book", 60, QuestType.WEEKLY, createReq(Material.ENCHANTED_BOOK, 1)));
        Quest q118 = new Quest(118, "Kill 50 mobs", 50, QuestType.WEEKLY);
        q118.objective = ObjectiveType.KILL;
        q118.targetCount = 50;
        q118.targetEntity = null; // любые мобы
        w.add(q118);
        w.add(new Quest(119, "Build a redstone contraption", 55, QuestType.WEEKLY, createReq(Material.REDSTONE, 64)));
        w.add(new Quest(120, "Collect 500 experience points", 45, QuestType.WEEKLY));
        Quest q121 = new Quest(121, "Travel 500 blocks", 60, QuestType.WEEKLY);
        q121.objective = ObjectiveType.TRAVEL;
        q121.targetCount = 500;
        w.add(q121);
        w.add(new Quest(122, "Craft 10 shulker boxes", 70, QuestType.WEEKLY, createReq(Material.SHULKER_BOX, 10)));
        w.add(new Quest(123, "Smelt 200 ores", 50, QuestType.WEEKLY));
        w.add(new Quest(124, "Collect 32 nether quartz", 40, QuestType.WEEKLY, createReq(Material.QUARTZ, 32)));
        w.add(new Quest(125, "Kill 10 ghasts", 80, QuestType.WEEKLY) {{
            objective = ObjectiveType.KILL; targetCount = 10; targetEntity = EntityType.GHAST;
        }});
        weeklyQuests = w;
    }

    // --- Система отношений ---
    public static class PendingRelationRequest {
        public String fromClan;
        public String toClan;
        public Clan.Relationship relation;
        public PendingRelationRequest(String fromClan, String toClan, Clan.Relationship relation) {
            this.fromClan = fromClan;
            this.toClan = toClan;
            this.relation = relation;
        }
    }
    // --- Конец системы отношений ---

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("clan") != null) {
            getCommand("clan").setTabCompleter(this);
        }
        if (getCommand("clans") != null) {
            getCommand("clans").setTabCompleter(this);
        }

        getLogger().info("GoonClansEngine enabled!");
        loadClans();
        long now = System.currentTimeMillis();
        dailyResetTime = now + 24 * 3600 * 1000;   // 24 часа
        weeklyResetTime = now + 7 * 24 * 3600 * 1000; // 7 дней

        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerDisplay(player);
        }
    }

    @Override
    public void onDisable() {
        saveClans();
        getLogger().info("GoonClansEngine disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("clan")) {
            if (args.length < 1) {
                player.sendMessage(ChatColor.RED + "Usage: /clan <create|invite|accept|decline|join|kick|leave|disband|delete|transfer|setdesc|settag|rename|logs|points|admin|setrelation|friendaccept|sharepoints|quest|shop|chest|sethome|home|debug|open|close>");
                return true;
            }
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "create":
                    // /clan create <tag> <name>
                    if (args.length != 3) {
                        player.sendMessage(ChatColor.RED + "Usage: /clan create <tag> <name> (both must be a single word)");
                        return true;
                    }
                    if (getPlayerClan(player.getUniqueId()) != null) {
                        player.sendMessage(ChatColor.RED + "You are already in a clan.");
                        return true;
                    }
                    String tag = ChatColor.translateAlternateColorCodes('&', args[1]);
                    if (tag.length() > MAX_TAG_LENGTH) {
                        player.sendMessage(ChatColor.RED + "Tag cannot exceed " + MAX_TAG_LENGTH + " characters.");
                        return true;
                    }
                    String clanName = args[2].trim();
                    if (clanName.length() > MAX_CLAN_NAME_LENGTH) {
                        player.sendMessage(ChatColor.RED + "Clan name cannot exceed " + MAX_CLAN_NAME_LENGTH + " characters.");
                        return true;
                    }
                    if (clans.containsKey(clanName)) {
                        player.sendMessage(ChatColor.RED + "A clan with that name already exists!");
                        return true;
                    }
                    Clan clan = new Clan(clanName, tag, player.getUniqueId());
                    clan.addMember(player.getUniqueId());
                    clan.setMaxMembers(6); // По умолчанию максимум 6 участников
                    clan.setPoints(0);
                    clan.addLog("Clan created by " + player.getName());
                    clans.put(clanName, clan);
                    player.sendMessage(ChatColor.GREEN + "Clan " + clanName + " created with tag: " + clan.getFormattedTag());
                    updatePlayerDisplay(player);
                    saveClans();
                    break;
                case "invite":
                    if (args.length != 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /clan invite <player>");
                        return true;
                    }
                    if (inviteCooldown.containsKey(player.getUniqueId())) {
                        long last = inviteCooldown.get(player.getUniqueId());
                        if (System.currentTimeMillis() - last < INVITE_COOLDOWN_MS) {
                            player.sendMessage(ChatColor.RED + "Please wait before sending another invite.");
                            return true;
                        }
                    }
                    String targetName = args[1];
                    OfflinePlayer targetOffline = Bukkit.getOfflinePlayer(targetName);
                    if (targetOffline == null || (!targetOffline.hasPlayedBefore() && !targetOffline.isOnline())) {
                        player.sendMessage(ChatColor.RED + "Player not found.");
                        return true;
                    }
                    UUID targetUUID = targetOffline.getUniqueId();
                    if (getPlayerClan(targetUUID) != null) {
                        player.sendMessage(ChatColor.RED + "That player is already in a clan.");
                        return true;
                    }
                    Clan playerClan = getPlayerClan(player.getUniqueId());
                    if (playerClan == null) {
                        player.sendMessage(ChatColor.RED + "You are not in a clan.");
                        return true;
                    }
                    if (!playerClan.isLeader(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "Only the clan leader can invite players.");
                        return true;
                    }
                    if (playerClan.getInvites().contains(targetUUID)) {
                        player.sendMessage(ChatColor.RED + "Player has already been invited.");
                        return true;
                    }
                    playerClan.addInvite(targetUUID);
                    inviteCooldown.put(player.getUniqueId(), System.currentTimeMillis());
                    playerClan.addLog(player.getName() + " invited " + targetName);
                    player.sendMessage(ChatColor.GREEN + "Invitation sent to " + targetName);
                    if (targetOffline.isOnline()) {
                        Player targetOnline = Bukkit.getPlayer(targetUUID);
                        if (targetOnline != null) {
                            targetOnline.sendMessage(ChatColor.AQUA + "You have been invited to join clan " + playerClan.getFormattedTag() + " " + playerClan.getName() + ". Use /clan accept " + playerClan.getName() + " to join.");
                        }
                    }
                    saveClans();
                    break;
                case "accept":
                    if (args.length != 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /clan accept <clanName>");
                        return true;
                    }
                    String acceptClanName = args[1];
                    Clan inviteClan = clans.get(acceptClanName);
                    if (inviteClan == null) {
                        player.sendMessage(ChatColor.RED + "Clan not found.");
                        return true;
                    }
                    if (!inviteClan.getInvites().contains(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "You have not been invited to this clan.");
                        return true;
                    }
                    if (inviteClan.getMembers().size() >= inviteClan.getMaxMembers()) {
                        player.sendMessage(ChatColor.RED + "Clan is full.");
                        return true;
                    }
                    inviteClan.addMember(player.getUniqueId());
                    inviteClan.removeInvite(player.getUniqueId());
                    inviteClan.addLog(player.getName() + " accepted the invitation and joined the clan.");
                    player.sendMessage(ChatColor.GREEN + "You have joined clan " + inviteClan.getFormattedTag() + " " + inviteClan.getName());
                    updatePlayerDisplay(player);
                    saveClans();
                    break;
                case "decline":
                    if (args.length != 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /clan decline <clanName>");
                        return true;
                    }
                    String declineClanName = args[1];
                    Clan declineClan = clans.get(declineClanName);
                    if (declineClan == null) {
                        player.sendMessage(ChatColor.RED + "Clan not found.");
                        return true;
                    }
                    if (!declineClan.getInvites().contains(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "You don't have an invitation from clan " + declineClanName);
                        return true;
                    }
                    declineClan.removeInvite(player.getUniqueId());
                    declineClan.addLog(player.getName() + " declined the invitation.");
                    player.sendMessage(ChatColor.GREEN + "You declined the invitation from clan " + declineClanName);
                    saveClans();
                    break;
                case "join":
                    if (args.length != 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /clan join <clanName>");
                        return true;
                    }
                    if (joinRequestCooldown.containsKey(player.getUniqueId())) {
                        long last = joinRequestCooldown.get(player.getUniqueId());
                        if (System.currentTimeMillis() - last < JOIN_REQUEST_COOLDOWN_MS) {
                            player.sendMessage(ChatColor.RED + "Please wait before sending another join request.");
                            return true;
                        }
                    }
                    String joinClanName = args[1];
                    Clan joinClan = clans.get(joinClanName);
                    if (joinClan == null) {
                        player.sendMessage(ChatColor.RED + "Clan not found.");
                        return true;
                    }
                    if (getPlayerClan(player.getUniqueId()) != null) {
                        player.sendMessage(ChatColor.RED + "You are already in a clan.");
                        return true;
                    }
                    if (joinClan.isOpen()) {
                        if (joinClan.getMembers().size() >= joinClan.getMaxMembers()) {
                            player.sendMessage(ChatColor.RED + "Clan is full.");
                            return true;
                        }
                        joinClan.addMember(player.getUniqueId());
                        joinClan.addLog(player.getName() + " joined the clan (open clan).");
                        player.sendMessage(ChatColor.GREEN + "You have joined clan " + joinClan.getName());
                        updatePlayerDisplay(player);
                        saveClans();
                        return true;
                    }
                    joinClan.addJoinRequest(player.getUniqueId());
                    joinRequestCooldown.put(player.getUniqueId(), System.currentTimeMillis());
                    player.sendMessage(ChatColor.GREEN + "Join request sent to clan " + joinClan.getName() + ".");
                    Player leader = Bukkit.getPlayer(joinClan.getLeader());
                    if (leader != null) {
                        leader.sendMessage(ChatColor.AQUA + player.getName() + " has requested to join your clan. Use /clan invite " + player.getName() + " to invite.");
                    }
                    joinClan.addLog(player.getName() + " requested to join the clan.");
                    saveClans();
                    break;
                case "kick":
                    if (args.length != 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /clan kick <player>");
                        return true;
                    }
                    String kickName = args[1];
                    Player kickPlayer = Bukkit.getPlayerExact(kickName);
                    if (kickPlayer == null) {
                        player.sendMessage(ChatColor.RED + "Player not found.");
                        return true;
                    }
                    Clan clanToKick = getPlayerClan(player.getUniqueId());
                    if (clanToKick == null) {
                        player.sendMessage(ChatColor.RED + "You are not in a clan.");
                        return true;
                    }
                    if (!clanToKick.isLeader(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "Only the clan leader can kick players.");
                        return true;
                    }
                    if (!clanToKick.getMembers().contains(kickPlayer.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "That player is not in your clan.");
                        return true;
                    }
                    clanToKick.removeMember(kickPlayer.getUniqueId());
                    clanToKick.addLog(player.getName() + " kicked " + kickPlayer.getName());
                    player.sendMessage(ChatColor.GREEN + kickPlayer.getName() + " has been kicked from the clan.");
                    kickPlayer.sendMessage(ChatColor.RED + "You have been kicked from clan " + clanToKick.getName());
                    updatePlayerDisplay(kickPlayer);
                    saveClans();
                    break;
                case "leave":
                    Clan ownClan = getPlayerClan(player.getUniqueId());
                    if (ownClan == null) {
                        player.sendMessage(ChatColor.RED + "You are not in a clan.");
                        return true;
                    }
                    if (ownClan.isLeader(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "The leader cannot leave the clan. Consider transferring leadership or disbanding the clan.");
                        return true;
                    }
                    ownClan.removeMember(player.getUniqueId());
                    ownClan.addLog(player.getName() + " left the clan.");
                    player.sendMessage(ChatColor.GREEN + "You have left clan " + ownClan.getName());
                    updatePlayerDisplay(player);
                    saveClans();
                    break;
                case "disband":
                    Clan clanToDisband = getPlayerClan(player.getUniqueId());
                    if (clanToDisband == null) {
                        player.sendMessage(ChatColor.RED + "You are not in a clan.");
                        return true;
                    }
                    if (!clanToDisband.isLeader(player.getUniqueId()) && !player.isOp()) {
                        player.sendMessage(ChatColor.RED + "Only the clan leader or an operator can disband the clan.");
                        return true;
                    }
                    Set<UUID> members = new HashSet<>(clanToDisband.getMembers());
                    clans.remove(clanToDisband.getName());
                    clanToDisband.addLog(player.getName() + " disbanded the clan.");
                    for (UUID memberUUID : members) {
                        Player member = Bukkit.getPlayer(memberUUID);
                        if (member != null) {
                            member.sendMessage(ChatColor.RED + "Your clan " + clanToDisband.getName() + " has been disbanded.");
                            updatePlayerDisplay(member);
                        }
                    }
                    player.sendMessage(ChatColor.GREEN + "Clan " + clanToDisband.getName() + " has been disbanded.");
                    saveClans();
                    break;
                case "delete":
                    if (!player.isOp()) {
                        player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                        return true;
                    }
                    if (args.length != 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /clan delete <clanName>");
                        return true;
                    }
                    String delClanName = args[1];
                    Clan delClan = clans.get(delClanName);
                    if (delClan == null) {
                        player.sendMessage(ChatColor.RED + "Clan not found.");
                        return true;
                    }
                    clans.remove(delClanName);
                    delClan.addLog("Clan deleted by operator " + player.getName());
                    for (UUID memberUUID : delClan.getMembers()) {
                        Player member = Bukkit.getPlayer(memberUUID);
                        if (member != null) {
                            member.sendMessage(ChatColor.RED + "Your clan " + delClan.getName() + " has been deleted by an operator.");
                            updatePlayerDisplay(member);
                        }
                    }
                    player.sendMessage(ChatColor.GREEN + "Clan " + delClanName + " has been deleted.");
                    saveClans();
                    break;
                case "transfer":
                    if (args.length != 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /clan transfer <player>");
                        return true;
                    }
                    Clan currentClan = getPlayerClan(player.getUniqueId());
                    if (currentClan == null) {
                        player.sendMessage(ChatColor.RED + "You are not in a clan.");
                        return true;
                    }
                    if (!currentClan.isLeader(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "Only the clan leader can transfer leadership.");
                        return true;
                    }
                    String newLeaderName = args[1];
                    Player newLeader = Bukkit.getPlayerExact(newLeaderName);
                    if (newLeader == null) {
                        player.sendMessage(ChatColor.RED + "Player not found.");
                        return true;
                    }
                    if (newLeader.getUniqueId().equals(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "You are already the leader.");
                        return true;
                    }
                    if (!currentClan.getMembers().contains(newLeader.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "That player is not in your clan.");
                        return true;
                    }
                    currentClan.setLeader(newLeader.getUniqueId());
                    currentClan.addLog("Leadership transferred from " + player.getName() + " to " + newLeader.getName());
                    player.sendMessage(ChatColor.GREEN + "Leadership transferred to " + newLeader.getName());
                    newLeader.sendMessage(ChatColor.GREEN + "You are now the leader of clan " + currentClan.getName() + " and have access to logs.");
                    for (UUID memberUUID : currentClan.getMembers()) {
                        Player member = Bukkit.getPlayer(memberUUID);
                        if (member != null) {
                            updatePlayerDisplay(member);
                        }
                    }
                    saveClans();
                    break;
                case "setdesc":
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /clan setdesc <description>");
                        return true;
                    }
                    Clan clanForDesc = getPlayerClan(player.getUniqueId());
                    if (clanForDesc == null) {
                        player.sendMessage(ChatColor.RED + "You are not in a clan.");
                        return true;
                    }
                    if (!clanForDesc.isLeader(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "Only the clan leader can set the description.");
                        return true;
                    }
                    String description = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    clanForDesc.setDescription(description);
                    clanForDesc.addLog(player.getName() + " changed clan description.");
                    player.sendMessage(ChatColor.GREEN + "Clan description updated.");
                    for (UUID memberUUID : clanForDesc.getMembers()) {
                        Player member = Bukkit.getPlayer(memberUUID);
                        if (member != null && !member.equals(player)) {
                            member.sendMessage(ChatColor.AQUA + "Clan description has been updated.");
                        }
                    }
                    saveClans();
                    break;
                case "settag":
                    if (args.length != 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /clan settag <newTag>");
                        return true;
                    }
                    Clan clanForTag = getPlayerClan(player.getUniqueId());
                    if (clanForTag == null) {
                        player.sendMessage(ChatColor.RED + "You are not in a clan.");
                        return true;
                    }
                    if (!clanForTag.isLeader(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "Only the clan leader can change the tag.");
                        return true;
                    }
                    String newTag = ChatColor.translateAlternateColorCodes('&', args[1]);
                    if (newTag.length() > MAX_TAG_LENGTH) {
                        player.sendMessage(ChatColor.RED + "Tag cannot exceed " + MAX_TAG_LENGTH + " characters.");
                        return true;
                    }
                    clanForTag.setTag(newTag);
                    clanForTag.addLog(player.getName() + " changed clan tag to " + newTag);
                    player.sendMessage(ChatColor.GREEN + "Clan tag updated to: " + clanForTag.getFormattedTag());
                    for (UUID memberUUID : clanForTag.getMembers()) {
                        Player member = Bukkit.getPlayer(memberUUID);
                        if (member != null && !member.equals(player)) {
                            member.sendMessage(ChatColor.AQUA + "Clan tag has been updated.");
                            updatePlayerDisplay(member);
                        }
                    }
                    saveClans();
                    break;
                case "rename":
                    if (args.length != 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /clan rename <newName>");
                        return true;
                    }
                    Clan clanToRename = getPlayerClan(player.getUniqueId());
                    if (clanToRename == null) {
                        player.sendMessage(ChatColor.RED + "You are not in a clan.");
                        return true;
                    }
                    if (!clanToRename.isLeader(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "Only the clan leader can rename the clan.");
                        return true;
                    }
                    String newName = args[1].trim();
                    if (newName.length() > MAX_CLAN_NAME_LENGTH) {
                        player.sendMessage(ChatColor.RED + "Clan name cannot exceed " + MAX_CLAN_NAME_LENGTH + " characters.");
                        return true;
                    }
                    if (clans.containsKey(newName)) {
                        player.sendMessage(ChatColor.RED + "A clan with that name already exists!");
                        return true;
                    }
                    clans.remove(clanToRename.getName());
                    clanToRename.setName(newName);
                    clans.put(newName, clanToRename);
                    clanToRename.addLog(player.getName() + " renamed the clan to " + newName);
                    player.sendMessage(ChatColor.GREEN + "Clan renamed to: " + newName);
                    for (UUID memberUUID : clanToRename.getMembers()) {
                        Player member = Bukkit.getPlayer(memberUUID);
                        if (member != null) {
                            updatePlayerDisplay(member);
                        }
                    }
                    saveClans();
                    break;
                case "logs":
                    if (args.length == 2) {
                        if (!player.isOp()) {
                            player.sendMessage(ChatColor.RED + "You don't have permission to view other clans' logs.");
                            return true;
                        }
                        String logClanName = args[1];
                        Clan logClan = clans.get(logClanName);
                        if (logClan == null) {
                            player.sendMessage(ChatColor.RED + "Clan not found.");
                            return true;
                        }
                        player.sendMessage(ChatColor.GOLD + "Logs for clan " + logClan.getName() + ":");
                        for (String log : logClan.getLogs()) {
                            player.sendMessage(ChatColor.GRAY + log);
                        }
                    } else {
                        Clan myClan = getPlayerClan(player.getUniqueId());
                        if (myClan == null) {
                            player.sendMessage(ChatColor.RED + "You are not in a clan.");
                            return true;
                        }
                        if (!myClan.isLeader(player.getUniqueId())) {
                            player.sendMessage(ChatColor.RED + "Only clan leaders can view logs.");
                            return true;
                        }
                        player.sendMessage(ChatColor.GOLD + "Logs for your clan " + myClan.getName() + ":");
                        for (String log : myClan.getLogs()) {
                            player.sendMessage(ChatColor.GRAY + log);
                        }
                    }
                    break;
                case "points":
                    Clan pointsClan = getPlayerClan(player.getUniqueId());
                    if (pointsClan == null) {
                        player.sendMessage(ChatColor.RED + "You are not in a clan.");
                        return true;
                    }
                    player.sendMessage(ChatColor.GOLD + "Clan Points: " + pointsClan.getPoints());
                    break;
                case "admin":
                    if (!player.isOp()) {
                        player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                        return true;
                    }
                    if (args.length != 4 || !args[1].equalsIgnoreCase("addpoints")) {
                        player.sendMessage(ChatColor.RED + "Usage: /clan admin addpoints <clanName> <amount>");
                        return true;
                    }
                    String apClanName = args[2];
                    Clan apClan = clans.get(apClanName);
                    if (apClan == null) {
                        player.sendMessage(ChatColor.RED + "Clan not found.");
                        return true;
                    }
                    int amount;
                    try {
                        amount = Integer.parseInt(args[3]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Amount must be a number.");
                        return true;
                    }
                    apClan.addPoints(amount);
                    apClan.addLog("Operator " + player.getName() + " added " + amount + " points.");
                    player.sendMessage(ChatColor.GREEN + "Added " + amount + " points to clan " + apClan.getName());
                    saveClans();
                    break;
                case "setrelation":
                    if (args.length != 3) {
                        player.sendMessage(ChatColor.RED + "Usage: /clan setrelation <otherClanName> <friend|enemy|neutral>");
                        return true;
                    }
                    Clan myClanRel = getPlayerClan(player.getUniqueId());
                    if (myClanRel == null) {
                        player.sendMessage(ChatColor.RED + "You are not in a clan.");
                        return true;
                    }
                    if (!myClanRel.isLeader(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "Only clan leaders can set relations.");
                        return true;
                    }
                    String otherClanName = args[1];
                    if (otherClanName.equalsIgnoreCase(myClanRel.getName())) {
                        player.sendMessage(ChatColor.RED + "You cannot set relationship with your own clan.");
                        return true;
                    }
                    Clan otherClan = clans.get(otherClanName);
                    if (otherClan == null) {
                        player.sendMessage(ChatColor.RED + "Other clan not found.");
                        return true;
                    }
                    String relationStr = args[2].toLowerCase();
                    if (relationStr.equals("friend")) {
                        PendingRelationRequest req = new PendingRelationRequest(myClanRel.getName(), otherClan.getName(), Clan.Relationship.FRIEND);
                        List<PendingRelationRequest> list = pendingFriendRequests.getOrDefault(otherClan.getName(), new ArrayList<>());
                        list.add(req);
                        pendingFriendRequests.put(otherClan.getName(), list);
                        myClanRel.addLog("Friend relationship request sent to clan " + otherClan.getName());
                        player.sendMessage(ChatColor.GREEN + "Friend relationship request sent to clan " + otherClan.getName() + ". Waiting for approval.");
                        Player otherLeader = Bukkit.getPlayer(otherClan.getLeader());
                        if (otherLeader != null) {
                            otherLeader.sendMessage(ChatColor.AQUA + "Clan " + myClanRel.getName() + " requests friendship with your clan. Type /clan friendaccept " + myClanRel.getName() + " to accept.");
                        }
                    } else {
                        Clan.Relationship relation;
                        if (relationStr.equals("enemy")) {
                            relation = Clan.Relationship.ENEMY;
                        } else if (relationStr.equals("neutral")) {
                            relation = Clan.Relationship.NEUTRAL;
                        } else {
                            player.sendMessage(ChatColor.RED + "Invalid relationship type. Use friend, enemy, or neutral.");
                            return true;
                        }
                        myClanRel.setRelationship(otherClan.getName(), relation);
                        otherClan.setRelationship(myClanRel.getName(), relation);
                        myClanRel.addLog("Relationship with " + otherClan.getName() + " set to " + relation.toString().toLowerCase());
                        otherClan.addLog("Relationship with " + myClanRel.getName() + " set to " + relation.toString().toLowerCase());
                        player.sendMessage(ChatColor.GREEN + "Relationship with clan " + otherClan.getName() + " set to " + relation.toString().toLowerCase() + ".");
                        Player otherLeader = Bukkit.getPlayer(otherClan.getLeader());
                        if (otherLeader != null) {
                            otherLeader.sendMessage(ChatColor.AQUA + "Clan " + myClanRel.getName() + " set relationship to " + relation.toString().toLowerCase() + " with your clan.");
                        }
                        for (UUID memberUUID : myClanRel.getMembers()) {
                            Player member = Bukkit.getPlayer(memberUUID);
                            if (member != null) {
                                member.sendMessage(ChatColor.YELLOW + "Your clan relationship with " + otherClan.getName() + " is now " + relation.toString().toLowerCase() + ".");
                            }
                        }
                        for (UUID memberUUID : otherClan.getMembers()) {
                            Player member = Bukkit.getPlayer(memberUUID);
                            if (member != null) {
                                member.sendMessage(ChatColor.YELLOW + "Your clan relationship with " + myClanRel.getName() + " is now " + relation.toString().toLowerCase() + ".");
                            }
                        }
                        saveClans();
                    }
                    break;
                case "friendaccept":
                    if (args.length != 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /clan friendaccept <fromClanName>");
                        return true;
                    }
                    Clan myClanFriend = getPlayerClan(player.getUniqueId());
                    if (myClanFriend == null) {
                        player.sendMessage(ChatColor.RED + "You are not in a clan.");
                        return true;
                    }
                    if (!myClanFriend.isLeader(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "Only clan leaders can accept friend requests.");
                        return true;
                    }
                    String fromClanName = args[1];
                    List<PendingRelationRequest> requests = pendingFriendRequests.getOrDefault(myClanFriend.getName(), new ArrayList<>());
                    PendingRelationRequest foundReq = null;
                    for (PendingRelationRequest req : requests) {
                        if (req.fromClan.equals(fromClanName)) {
                            foundReq = req;
                            break;
                        }
                    }
                    if (foundReq == null) {
                        player.sendMessage(ChatColor.RED + "No pending friend request from clan " + fromClanName);
                        return true;
                    }
                    Clan fromClan = clans.get(fromClanName);
                    if (fromClan == null) {
                        player.sendMessage(ChatColor.RED + "Requesting clan not found.");
                        return true;
                    }
                    myClanFriend.setRelationship(fromClan.getName(), Clan.Relationship.FRIEND);
                    fromClan.setRelationship(myClanFriend.getName(), Clan.Relationship.FRIEND);
                    myClanFriend.addLog("Friend relationship established with clan " + fromClan.getName());
                    fromClan.addLog("Friend relationship established with clan " + myClanFriend.getName());
                    player.sendMessage(ChatColor.GREEN + "Friend relationship established with clan " + fromClan.getName());
                    Player fromLeader = Bukkit.getPlayer(fromClan.getLeader());
                    if (fromLeader != null) {
                        fromLeader.sendMessage(ChatColor.AQUA + "Friend relationship established with clan " + myClanFriend.getName());
                    }
                    for (UUID memberUUID : myClanFriend.getMembers()) {
                        Player member = Bukkit.getPlayer(memberUUID);
                        if (member != null) {
                            member.sendMessage(ChatColor.GREEN + "Your clan is now friends with " + fromClan.getName());
                        }
                    }
                    for (UUID memberUUID : fromClan.getMembers()) {
                        Player member = Bukkit.getPlayer(memberUUID);
                        if (member != null) {
                            member.sendMessage(ChatColor.GREEN + "Your clan is now friends with " + myClanFriend.getName());
                        }
                    }
                    requests.remove(foundReq);
                    if (requests.isEmpty()) {
                        pendingFriendRequests.remove(myClanFriend.getName());
                    } else {
                        pendingFriendRequests.put(myClanFriend.getName(), requests);
                    }
                    saveClans();
                    break;
                case "sharepoints":
                    if (args.length != 3) {
                        player.sendMessage(ChatColor.RED + "Usage: /clan sharepoints <otherClanName> <amount>");
                        return true;
                    }
                    Clan myClanShare = getPlayerClan(player.getUniqueId());
                    if (myClanShare == null) {
                        player.sendMessage(ChatColor.RED + "You are not in a clan.");
                        return true;
                    }
                    if (!myClanShare.isLeader(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "Only the clan leader can share points with friendly clans.");
                        return true;
                    }
                    String friendClanName = args[1];
                    Clan friendClan = clans.get(friendClanName);
                    if (friendClan == null) {
                        player.sendMessage(ChatColor.RED + "Clan " + friendClanName + " not found.");
                        return true;
                    }
                    if (!myClanShare.getRelationships().containsKey(friendClanName) ||
                        myClanShare.getRelationships().get(friendClanName) != Clan.Relationship.FRIEND) {
                        player.sendMessage(ChatColor.RED + "Your clan is not friends with " + friendClanName + ".");
                        return true;
                    }
                    int shareAmount;
                    try {
                        shareAmount = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Amount must be a valid number.");
                        return true;
                    }
                    if (shareAmount <= 0) {
                        player.sendMessage(ChatColor.RED + "Amount must be greater than zero.");
                        return true;
                    }
                    if (myClanShare.getPoints() < shareAmount) {
                        player.sendMessage(ChatColor.RED + "Your clan does not have enough points to share.");
                        return true;
                    }
                    myClanShare.subtractPoints(shareAmount);
                    friendClan.addPoints(shareAmount);
                    myClanShare.addLog(player.getName() + " shared " + shareAmount + " points with clan " + friendClan.getName());
                    friendClan.addLog("Clan " + myClanShare.getName() + " shared " + shareAmount + " points with your clan");
                    player.sendMessage(ChatColor.GREEN + "Successfully shared " + shareAmount + " points with clan " + friendClan.getName());
                    Player friendLeader = Bukkit.getPlayer(friendClan.getLeader());
                    if (friendLeader != null) {
                        friendLeader.sendMessage(ChatColor.AQUA + "Your clan received " + shareAmount + " points from clan " + myClanShare.getName());
                    }
                    for (UUID memberUUID : friendClan.getMembers()) {
                        Player member = Bukkit.getPlayer(memberUUID);
                        if (member != null) {
                            member.sendMessage(ChatColor.AQUA + "Your clan received " + shareAmount + " points from clan " + myClanShare.getName());
                        }
                    }
                    saveClans();
                    break;
                case "quest":
                    long currentTime = System.currentTimeMillis();
                    if (currentTime >= dailyResetTime) {
                        clanDailyQuestsCompleted.clear();
                        dailyResetTime = currentTime + 24 * 3600 * 1000;
                    }
                    if (currentTime >= weeklyResetTime) {
                        clanWeeklyQuestsCompleted.clear();
                        weeklyResetTime = currentTime + 7 * 24 * 3600 * 1000;
                    }
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /clan quest <list|complete|reset> [questID/resetType]");
                        return true;
                    }
                    // Новая возможность для операторов сбрасывать квесты
                    if (args[1].equalsIgnoreCase("reset")) {
                        if (!player.isOp()) {
                            player.sendMessage(ChatColor.RED + "You do not have permission to reset quests.");
                            return true;
                        }
                        if (args.length != 3) {
                            player.sendMessage(ChatColor.RED + "Usage: /clan quest reset <daily|weekly|all>");
                            return true;
                        }
                        String resetType = args[2].toLowerCase();
                        long now = System.currentTimeMillis();
                        switch (resetType) {
                            case "daily":
                                clanDailyQuestsCompleted.clear();
                                dailyResetTime = now + 24 * 3600 * 1000;
                                player.sendMessage(ChatColor.GREEN + "Daily quests have been reset.");
                                break;
                            case "weekly":
                                clanWeeklyQuestsCompleted.clear();
                                weeklyResetTime = now + 7 * 24 * 3600 * 1000;
                                player.sendMessage(ChatColor.GREEN + "Weekly quests have been reset.");
                                break;
                            case "all":
                                clanDailyQuestsCompleted.clear();
                                clanWeeklyQuestsCompleted.clear();
                                dailyResetTime = now + 24 * 3600 * 1000;
                                weeklyResetTime = now + 7 * 24 * 3600 * 1000;
                                player.sendMessage(ChatColor.GREEN + "Daily and weekly quests have been reset.");
                                break;
                            default:
                                player.sendMessage(ChatColor.RED + "Invalid reset type. Use daily, weekly, or all.");
                                break;
                        }
                        return true;
                    }
                    if (args[1].equalsIgnoreCase("list")) {
                        Clan questClan = getPlayerClan(player.getUniqueId());
                        if (questClan == null) {
                            player.sendMessage(ChatColor.RED + "You are not in a clan.");
                            return true;
                        }
                        player.sendMessage(ChatColor.GOLD + "=== Daily Quests ===");
                        Set<Integer> dailyCompleted = clanDailyQuestsCompleted.getOrDefault(questClan.getName(), new HashSet<>());
                        for (Quest q : dailyQuests) {
                            String reqStr = formatRequirements(q.requirements);
                            String status = dailyCompleted.contains(q.id) ? ChatColor.GREEN + "COMPLETED" : ChatColor.RED + "INCOMPLETE";
                            player.sendMessage(ChatColor.YELLOW.toString() + q.id + ". " + q.description +
                                    (reqStr.isEmpty() ? "" : " (Requires: " + reqStr + ")") +
                                    " - Reward: " + q.reward + " points [" + status + ChatColor.YELLOW.toString() + "]");
                        }
                        player.sendMessage(ChatColor.GOLD + "=== Weekly Quests ===");
                        Set<Integer> weeklyCompleted = clanWeeklyQuestsCompleted.getOrDefault(questClan.getName(), new HashSet<>());
                        for (Quest q : weeklyQuests) {
                            String reqStr = formatRequirements(q.requirements);
                            String status = weeklyCompleted.contains(q.id) ? ChatColor.GREEN + "COMPLETED" : ChatColor.RED + "INCOMPLETE";
                            player.sendMessage(ChatColor.YELLOW.toString() + q.id + ". " + q.description +
                                    (reqStr.isEmpty() ? "" : " (Requires: " + reqStr + ")") +
                                    " - Reward: " + q.reward + " points [" + status + ChatColor.YELLOW.toString() + "]");
                        }
                        return true;
                    } else if (args[1].equalsIgnoreCase("complete")) {
                        if (args.length != 3) {
                            player.sendMessage(ChatColor.RED + "Usage: /clan quest complete <questID>");
                            return true;
                        }
                        int questId;
                        try {
                            questId = Integer.parseInt(args[2]);
                        } catch (NumberFormatException e) {
                            player.sendMessage(ChatColor.RED + "Quest ID must be a number.");
                            return true;
                        }
                        Quest quest = null;
                        if (questId <= 100) {
                            for (Quest q : dailyQuests) {
                                if (q.id == questId) { quest = q; break; }
                            }
                        } else {
                            for (Quest q : weeklyQuests) {
                                if (q.id == questId) { quest = q; break; }
                            }
                        }
                        if (quest == null) {
                            player.sendMessage(ChatColor.RED + "Quest not found.");
                            return true;
                        }
                        Clan questClan = getPlayerClan(player.getUniqueId());
                        if (questClan == null) {
                            player.sendMessage(ChatColor.RED + "You are not in a clan.");
                            return true;
                        }
                        // Для квестов, требующих ресурсы, проверяем наличие и списываем их
                        if (!quest.requirements.isEmpty()) {
                            if (!checkAndRemoveRequirements(player, quest.requirements)) {
                                player.sendMessage(ChatColor.RED + "You do not have the required items to complete this quest.");
                                return true;
                            }
                        }
                        // Если квест автоматический, его выполнение должно происходить через эвенты – здесь вручную сдать можно только если не было автоматики
                        if (quest.objective != ObjectiveType.NONE) {
                            player.sendMessage(ChatColor.RED + "This quest is automatically tracked and cannot be manually submitted.");
                            return true;
                        }
                        if (quest.type == QuestType.DAILY) {
                            Set<Integer> completed = clanDailyQuestsCompleted.getOrDefault(questClan.getName(), new HashSet<>());
                            if (completed.contains(quest.id)) {
                                player.sendMessage(ChatColor.RED + "This daily quest has already been completed.");
                                return true;
                            }
                            completed.add(quest.id);
                            clanDailyQuestsCompleted.put(questClan.getName(), completed);
                        } else {
                            Set<Integer> completed = clanWeeklyQuestsCompleted.getOrDefault(questClan.getName(), new HashSet<>());
                            if (completed.contains(quest.id)) {
                                player.sendMessage(ChatColor.RED + "This weekly quest has already been completed.");
                                return true;
                            }
                            completed.add(quest.id);
                            clanWeeklyQuestsCompleted.put(questClan.getName(), completed);
                        }
                        questClan.addPoints(quest.reward);
                        questClan.addLog(player.getName() + " manually completed quest '" + quest.description + "' and earned " + quest.reward + " points.");
                        player.sendMessage(ChatColor.GREEN + "Quest completed! Your clan earned " + quest.reward + " points.");
                        saveClans();
                        return true;
                    } else {
                        player.sendMessage(ChatColor.RED + "Usage: /clan quest <list|complete|reset> [questID/resetType]");
                        return true;
                    }
                case "shop":
                    {
                        Clan shopClan = getPlayerClan(player.getUniqueId());
                        if (shopClan == null) {
                            player.sendMessage(ChatColor.RED + "You are not in a clan.");
                            return true;
                        }
                        // Теперь магазин доступен при наличии 2 участников (если не включен debug-режим)
                        if (!shopClan.isDebugOverride() && shopClan.getMemberCount() < 2) {
                            player.sendMessage(ChatColor.RED + "Clan shop is available only for clans with at least 2 members.");
                            return true;
                        }
                        if (args.length == 1) {
                            displayShopInfoModified(player);
                        } else if (args.length == 2 && args[1].equalsIgnoreCase("upgrade")) {
                            displayNextUpgradeInfo(player);
                        } else if (args.length == 3 && args[1].equalsIgnoreCase("upgrade") && args[2].equalsIgnoreCase("confirm")) {
                            processShopUpgradePurchase(player);
                        } else {
                            player.sendMessage(ChatColor.RED + "Usage: /clan shop OR /clan shop upgrade OR /clan shop upgrade confirm");
                        }
                    }
                    break;
                case "chest":
                    {
                        Clan chestClan = getPlayerClan(player.getUniqueId());
                        if (chestClan == null) {
                            player.sendMessage(ChatColor.RED + "You are not in a clan.");
                            return true;
                        }
                        if (!chestClan.hasSharedChest()) {
                            player.sendMessage(ChatColor.RED + "Your clan has not purchased a shared chest upgrade.");
                            return true;
                        }
                        Inventory inv = chestClan.getSharedChest();
                        int size = chestClan.hasDoubleChest() ? 54 : 27;
                        if (inv == null || inv.getSize() != size) {
                            inv = Bukkit.createInventory(null, size, ChatColor.GOLD + "Shared Clan Chest");
                            chestClan.setSharedChest(inv);
                        }
                        player.openInventory(inv);
                    }
                    break;
                case "sethome":
                    {
                        Clan homeClan = getPlayerClan(player.getUniqueId());
                        if (homeClan == null) {
                            player.sendMessage(ChatColor.RED + "You are not in a clan.");
                            return true;
                        }
                        if (!homeClan.isLeader(player.getUniqueId())) {
                            player.sendMessage(ChatColor.RED + "Only the clan leader can set the clan home.");
                            return true;
                        }
                        if (!homeClan.hasHome()) {
                            player.sendMessage(ChatColor.RED + "Your clan has not purchased the Clan Home Teleport Upgrade.");
                            return true;
                        }
                        clanHomes.put(homeClan.getName(), player.getLocation());
                        homeClan.addLog(player.getName() + " set the clan home.");
                        player.sendMessage(ChatColor.GREEN + "Clan home set to your current location.");
                        saveClans();
                    }
                    break;
                case "home":
                    {
                        Clan homeClan = getPlayerClan(player.getUniqueId());
                        if (homeClan == null) {
                            player.sendMessage(ChatColor.RED + "You are not in a clan.");
                            return true;
                        }
                        if (!homeClan.hasHome()) {
                            player.sendMessage(ChatColor.RED + "Your clan has not purchased the Clan Home Teleport Upgrade.");
                            return true;
                        }
                        if (!clanHomes.containsKey(homeClan.getName())) {
                            player.sendMessage(ChatColor.RED + "Clan home is not set.");
                            return true;
                        }
                        Long lastHit = lastCombat.get(player.getUniqueId());
                        if (lastHit != null && System.currentTimeMillis() - lastHit < PVP_PROTECTION_MS) {
                            player.sendMessage(ChatColor.RED + "You cannot teleport to clan home during or immediately after PvP.");
                            return true;
                        }
                        player.teleport(clanHomes.get(homeClan.getName()));
                        player.sendMessage(ChatColor.GREEN + "Teleported to clan home.");
                    }
                    break;
                case "debug":
                    if (!player.isOp()) {
                        player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                        return true;
                    }
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /clan debug <4|normal> [clanName]");
                        return true;
                    }
                    if (args[1].equalsIgnoreCase("4")) {
                        if (args.length != 3) {
                            player.sendMessage(ChatColor.RED + "Usage: /clan debug 4 <clanName>");
                            return true;
                        }
                        String debugClanName = args[2];
                        Clan debugClan = clans.get(debugClanName);
                        if (debugClan == null) {
                            player.sendMessage(ChatColor.RED + "Clan not found.");
                            return true;
                        }
                        debugClan.setDebugOverride(true);
                        debugClan.addLog("Debug override set to 4 by operator " + player.getName());
                        player.sendMessage(ChatColor.GREEN + "Debug override applied to clan " + debugClanName);
                        saveClans();
                    } else if (args[1].equalsIgnoreCase("normal")) {
                        if (args.length == 3) {
                            String debugClanName = args[2];
                            Clan debugClan = clans.get(debugClanName);
                            if (debugClan == null) {
                                player.sendMessage(ChatColor.RED + "Clan not found.");
                                return true;
                            }
                            debugClan.setDebugOverride(false);
                            debugClan.addLog("Debug override removed by operator " + player.getName());
                            player.sendMessage(ChatColor.GREEN + "Debug override removed from clan " + debugClanName);
                            saveClans();
                        } else {
                            Clan debugClan = getPlayerClan(player.getUniqueId());
                            if (debugClan == null) {
                                player.sendMessage(ChatColor.RED + "You are not in a clan, please specify a clan name.");
                                return true;
                            }
                            debugClan.setDebugOverride(false);
                            debugClan.addLog("Debug override removed by operator " + player.getName());
                            player.sendMessage(ChatColor.GREEN + "Debug override removed from clan " + debugClan.getName());
                            saveClans();
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "Usage: /clan debug <4|normal> [clanName]");
                    }
                    break;
                case "open":
                    {
                        Clan myClan = getPlayerClan(player.getUniqueId());
                        if (myClan == null) {
                            player.sendMessage(ChatColor.RED + "You are not in a clan.");
                            return true;
                        }
                        if (!myClan.isLeader(player.getUniqueId())) {
                            player.sendMessage(ChatColor.RED + "Only the clan leader can open the clan.");
                            return true;
                        }
                        myClan.setOpen(true);
                        myClan.addLog(player.getName() + " opened the clan.");
                        player.sendMessage(ChatColor.GREEN + "Your clan is now open for anyone to join with /clan join <clanName>.");
                        saveClans();
                    }
                    break;
                case "close":
                    {
                        Clan myClan = getPlayerClan(player.getUniqueId());
                        if (myClan == null) {
                            player.sendMessage(ChatColor.RED + "You are not in a clan.");
                            return true;
                        }
                        if (!myClan.isLeader(player.getUniqueId())) {
                            player.sendMessage(ChatColor.RED + "Only the clan leader can close the clan.");
                            return true;
                        }
                        myClan.setOpen(false);
                        myClan.addLog(player.getName() + " closed the clan.");
                        player.sendMessage(ChatColor.GREEN + "Your clan is now closed.");
                        saveClans();
                    }
                    break;
                default:
                    player.sendMessage(ChatColor.RED + "Unknown subcommand.");
                    break;
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("clans")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
                displayHelpWithExplanations(player);
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("info")) {
                if (args.length != 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /clans info <clanName>");
                    return true;
                }
                String infoClanName = args[1];
                Clan infoClan = clans.get(infoClanName);
                if (infoClan == null) {
                    player.sendMessage(ChatColor.RED + "Clan not found.");
                    return true;
                }
                sendClanInfo(player, infoClan);
                return true;
            }
            if (clans.isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "There are no clans yet.");
                return true;
            }
            player.sendMessage(ChatColor.AQUA + "List of clans:");
            for (Clan c : clans.values()) {
                String status = c.isOpen() ? ChatColor.GREEN + "[Open]" : ChatColor.RED + "[Closed]";
                player.sendMessage(c.getFormattedTag() + " " + c.getName() + " " + status + ChatColor.WHITE + " | Members: " + c.getMemberCount());
            }
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Для упрощения таб-комплита не реализован
        return null;
    }

    // --- Event Listeners ---

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        Player killer = event.getEntity().getKiller();
        Clan clan = getPlayerClan(killer.getUniqueId());
        if (clan == null) return;
        String clanName = clan.getName();
        // Обрабатываем DAILY квесты с целью KILL
        for (Quest quest : dailyQuests) {
            if (quest.objective == ObjectiveType.KILL) {
                Set<Integer> completed = clanDailyQuestsCompleted.getOrDefault(clanName, new HashSet<>());
                if (completed.contains(quest.id)) continue;
                if (quest.targetEntity != null && event.getEntity().getType() != quest.targetEntity) continue;
                Map<Integer, Integer> progress = clanObjectiveProgress.getOrDefault(clanName, new HashMap<>());
                int current = progress.getOrDefault(quest.id, 0);
                current++;
                progress.put(quest.id, current);
                clanObjectiveProgress.put(clanName, progress);
                if (current >= quest.targetCount) {
                    completed.add(quest.id);
                    clanDailyQuestsCompleted.put(clanName, completed);
                    clan.addPoints(quest.reward);
                    clan.addLog("Quest '" + quest.description + "' auto-completed (KILL) by " + killer.getName() + " for " + quest.reward + " points.");
                    killer.sendMessage(ChatColor.GREEN + "Daily quest '" + quest.description + "' completed! Your clan earned " + quest.reward + " points.");
                    saveClans();
                }
            }
        }
        // Обрабатываем WEEKLY квесты с целью KILL
        for (Quest quest : weeklyQuests) {
            if (quest.objective == ObjectiveType.KILL) {
                Set<Integer> completed = clanWeeklyQuestsCompleted.getOrDefault(clanName, new HashSet<>());
                if (completed.contains(quest.id)) continue;
                if (quest.targetEntity != null && event.getEntity().getType() != quest.targetEntity) continue;
                Map<Integer, Integer> progress = clanObjectiveProgress.getOrDefault(clanName, new HashMap<>());
                int current = progress.getOrDefault(quest.id, 0);
                current++;
                progress.put(quest.id, current);
                clanObjectiveProgress.put(clanName, progress);
                if (current >= quest.targetCount) {
                    completed.add(quest.id);
                    clanWeeklyQuestsCompleted.put(clanName, completed);
                    clan.addPoints(quest.reward);
                    clan.addLog("Quest '" + quest.description + "' auto-completed (KILL) by " + killer.getName() + " for " + quest.reward + " points.");
                    killer.sendMessage(ChatColor.GREEN + "Weekly quest '" + quest.description + "' completed! Your clan earned " + quest.reward + " points.");
                    saveClans();
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (event.getFrom().distance(event.getTo()) < 1) return; // Игнорируем незначительное перемещение
        Clan clan = getPlayerClan(player.getUniqueId());
        if (clan == null) return;
        String clanName = clan.getName();
        // Обрабатываем DAILY квесты с целью TRAVEL
        for (Quest quest : dailyQuests) {
            if (quest.objective == ObjectiveType.TRAVEL) {
                Set<Integer> completed = clanDailyQuestsCompleted.getOrDefault(clanName, new HashSet<>());
                if (completed.contains(quest.id)) continue;
                Map<Integer, Integer> progress = clanObjectiveProgress.getOrDefault(clanName, new HashMap<>());
                int current = progress.getOrDefault(quest.id, 0);
                double distance = event.getFrom().distance(event.getTo());
                current += (int) distance;
                progress.put(quest.id, current);
                clanObjectiveProgress.put(clanName, progress);
                if (current >= quest.targetCount) {
                    completed.add(quest.id);
                    clanDailyQuestsCompleted.put(clanName, completed);
                    clan.addPoints(quest.reward);
                    clan.addLog("Quest '" + quest.description + "' auto-completed (TRAVEL) by " + player.getName() + " for " + quest.reward + " points.");
                    player.sendMessage(ChatColor.GREEN + "Daily quest '" + quest.description + "' completed! Your clan earned " + quest.reward + " points.");
                    saveClans();
                }
            }
        }
        // Обрабатываем WEEKLY квесты с целью TRAVEL
        for (Quest quest : weeklyQuests) {
            if (quest.objective == ObjectiveType.TRAVEL) {
                Set<Integer> completed = clanWeeklyQuestsCompleted.getOrDefault(clanName, new HashSet<>());
                if (completed.contains(quest.id)) continue;
                Map<Integer, Integer> progress = clanObjectiveProgress.getOrDefault(clanName, new HashMap<>());
                int current = progress.getOrDefault(quest.id, 0);
                double distance = event.getFrom().distance(event.getTo());
                current += (int) distance;
                progress.put(quest.id, current);
                clanObjectiveProgress.put(clanName, progress);
                if (current >= quest.targetCount) {
                    completed.add(quest.id);
                    clanWeeklyQuestsCompleted.put(clanName, completed);
                    clan.addPoints(quest.reward);
                    clan.addLog("Quest '" + quest.description + "' auto-completed (TRAVEL) by " + player.getName() + " for " + quest.reward + " points.");
                    player.sendMessage(ChatColor.GREEN + "Weekly quest '" + quest.description + "' completed! Your clan earned " + quest.reward + " points.");
                    saveClans();
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            lastCombat.put(victim.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        updatePlayerDisplay(event.getPlayer());
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Clan clan = getPlayerClan(player.getUniqueId());
        if (clan != null) {
            event.setFormat(clan.getFormattedTag() + " " + player.getDisplayName() + ": " + event.getMessage());
        }
    }

    // --- Вспомогательные методы ---

    // Обновление отображения игрока (например, установка префикса с тегом клана)
    private void updatePlayerDisplay(Player player) {
        Clan clan = getPlayerClan(player.getUniqueId());
        if (clan != null) {
            player.setDisplayName(clan.getFormattedTag() + " " + player.getName());
        }
    }

    // Загрузка кланов (реализуйте загрузку из файла по своему усмотрению)
    private void loadClans() {
        // Пример: загрузка из Yaml-файла
        // Реализация опущена для краткости
    }

    // Сохранение кланов (реализуйте сохранение в файл по своему усмотрению)
    private void saveClans() {
        // Пример: сохранение в Yaml-файл
        // Реализация опущена для краткости
    }

    // Получение клана, в котором состоит игрок
    private Clan getPlayerClan(UUID uuid) {
        for (Clan clan : clans.values()) {
            if (clan.getMembers().contains(uuid)) {
                return clan;
            }
        }
        return null;
    }

    // Проверка наличия и удаление требуемых предметов из инвентаря игрока
    private boolean checkAndRemoveRequirements(Player player, Map<Material, Integer> requirements) {
        Inventory inv = player.getInventory();
        Map<Material, Integer> found = new HashMap<>();
        for (ItemStack item : inv.getContents()) {
            if (item == null) continue;
            Material mat = item.getType();
            if (requirements.containsKey(mat)) {
                int count = found.getOrDefault(mat, 0) + item.getAmount();
                found.put(mat, count);
            }
        }
        for (Map.Entry<Material, Integer> entry : requirements.entrySet()) {
            if (found.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        // Удаление предметов из инвентаря
        for (Map.Entry<Material, Integer> entry : requirements.entrySet()) {
            int toRemove = entry.getValue();
            for (ItemStack item : inv.getContents()) {
                if (item == null) continue;
                if (item.getType() == entry.getKey()) {
                    int amt = item.getAmount();
                    if (amt >= toRemove) {
                        item.setAmount(amt - toRemove);
                        break;
                    } else {
                        toRemove -= amt;
                        item.setAmount(0);
                    }
                }
            }
        }
        return true;
    }

    // Форматирование требований к квесту в строку
    private String formatRequirements(Map<Material, Integer> requirements) {
        if (requirements.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Material, Integer> entry : requirements.entrySet()) {
            sb.append(entry.getValue()).append(" ").append(entry.getKey().name()).append(", ");
        }
        return sb.substring(0, sb.length() - 2);
    }

    // Вывод информации о клане
    private void sendClanInfo(Player player, Clan clan) {
        player.sendMessage(ChatColor.GOLD + "Clan: " + clan.getName());
        player.sendMessage(ChatColor.YELLOW + "Tag: " + clan.getFormattedTag());
        player.sendMessage(ChatColor.YELLOW + "Description: " + clan.getDescription());
        player.sendMessage(ChatColor.YELLOW + "Members: " + clan.getMemberCount() + "/" + clan.getMaxMembers());
        player.sendMessage(ChatColor.YELLOW + "Points: " + clan.getPoints());
        // Дополнительно можно вывести список логов, отношений и т.д.
    }

    // Отображение информации о магазине клана
    private void displayShopInfoModified(Player player) {
        Clan clan = getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "You are not in a clan.");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "=== Clan Shop ===");
        player.sendMessage(ChatColor.YELLOW + "1. Shared Chest Upgrade - Cost: 50 points" + (clan.hasSharedChest() ? " [Purchased]" : ""));
        player.sendMessage(ChatColor.YELLOW + "2. Double Chest Capacity Upgrade - Cost: 100 points" + (clan.hasDoubleChest() ? " [Purchased]" : ""));
        player.sendMessage(ChatColor.YELLOW + "3. Clan Home Teleport Upgrade - Cost: 80 points" + (clan.hasHome() ? " [Purchased]" : ""));
        player.sendMessage(ChatColor.YELLOW + "4. Increase Member Limit Upgrade - Cost: 120 points" + (clan.hasExtraSlots() ? " [Purchased]" : ""));
        String nextUpgrade;
        if (!clan.hasSharedChest()) {
            nextUpgrade = "Shared Chest Upgrade (Cost: 50 points)";
        } else if (!clan.hasDoubleChest()) {
            nextUpgrade = "Double Chest Capacity Upgrade (Cost: 100 points)";
        } else if (!clan.hasHome()) {
            nextUpgrade = "Clan Home Teleport Upgrade (Cost: 80 points)";
        } else if (!clan.hasExtraSlots()) {
            nextUpgrade = "Increase Member Limit Upgrade (Cost: 120 points)";
        } else {
            nextUpgrade = "All upgrades purchased.";
        }
        player.sendMessage(ChatColor.GOLD + "Next available upgrade: " + ChatColor.YELLOW + nextUpgrade);
        if (!nextUpgrade.equals("All upgrades purchased.")) {
            player.sendMessage(ChatColor.GREEN + "To purchase the next upgrade, type /clan shop upgrade confirm");
        }
    }

    // Отображение информации о следующем доступном обновлении
    private void displayNextUpgradeInfo(Player player) {
        Clan clan = getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "You are not in a clan.");
            return;
        }
        String upgradeName = "";
        int cost = 0;
        if (!clan.hasSharedChest()) {
            upgradeName = "Shared Chest Upgrade";
            cost = 50;
        } else if (!clan.hasDoubleChest()) {
            upgradeName = "Double Chest Capacity Upgrade";
            cost = 100;
        } else if (!clan.hasHome()) {
            upgradeName = "Clan Home Teleport Upgrade";
            cost = 80;
        } else if (!clan.hasExtraSlots()) {
            upgradeName = "Increase Member Limit Upgrade";
            cost = 120;
        } else {
            player.sendMessage(ChatColor.GREEN + "All upgrades have been purchased.");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "Next Upgrade: " + ChatColor.YELLOW + upgradeName + " - Cost: " + cost + " points");
        player.sendMessage(ChatColor.GREEN + "To confirm the purchase, type /clan shop upgrade confirm");
    }

    // Обработка покупки обновления в магазине клана
    private void processShopUpgradePurchase(Player player) {
        Clan clan = getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "You are not in a clan.");
            return;
        }
        String upgradeName = "";
        int cost = 0;
        if (!clan.hasSharedChest()) {
            upgradeName = "Shared Chest Upgrade";
            cost = 50;
        } else if (!clan.hasDoubleChest()) {
            upgradeName = "Double Chest Capacity Upgrade";
            cost = 100;
        } else if (!clan.hasHome()) {
            upgradeName = "Clan Home Teleport Upgrade";
            cost = 80;
        } else if (!clan.hasExtraSlots()) {
            upgradeName = "Increase Member Limit Upgrade";
            cost = 120;
        } else {
            player.sendMessage(ChatColor.GREEN + "All upgrades have been purchased.");
            return;
        }
        if (clan.getPoints() < cost) {
            player.sendMessage(ChatColor.RED + "Your clan does not have enough points for this upgrade.");
            return;
        }
        clan.subtractPoints(cost);
        switch (upgradeName) {
            case "Shared Chest Upgrade":
                clan.setSharedChest(Bukkit.createInventory(null, 27, ChatColor.GOLD + "Shared Clan Chest"));
                clan.setHasSharedChest(true);
                break;
            case "Double Chest Capacity Upgrade":
                clan.setHasDoubleChest(true);
                if (clan.getSharedChest() != null && clan.getSharedChest().getSize() < 54) {
                    Inventory newInv = Bukkit.createInventory(null, 54, ChatColor.GOLD + "Shared Clan Chest");
                    for (ItemStack item : clan.getSharedChest().getContents()) {
                        newInv.addItem(item);
                    }
                    clan.setSharedChest(newInv);
                }
                break;
            case "Clan Home Teleport Upgrade":
                clan.setHasHome(true);
                break;
            case "Increase Member Limit Upgrade":
                clan.setExtraSlots(true);
                clan.setMaxMembers(clan.getMaxMembers() + 2);
                break;
        }
        clan.addLog(player.getName() + " purchased " + upgradeName + " for " + cost + " points.");
        player.sendMessage(ChatColor.GREEN + "Upgrade purchased: " + upgradeName);
        saveClans();
    }

    // Вывод справки по командам клана с пояснениями
    private void displayHelpWithExplanations(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Clan Commands Help ===");
        player.sendMessage(ChatColor.YELLOW + "/clan create <tag> <name> " + ChatColor.WHITE + "- Create a new clan (tag and name must be one word).");
        player.sendMessage(ChatColor.YELLOW + "/clan invite <player> " + ChatColor.WHITE + "- Invite a player to your clan (works for offline players).");
        player.sendMessage(ChatColor.YELLOW + "/clan accept <clanName> " + ChatColor.WHITE + "- Accept an invitation from a clan.");
        player.sendMessage(ChatColor.YELLOW + "/clan decline <clanName> " + ChatColor.WHITE + "- Decline an invitation from a clan.");
        player.sendMessage(ChatColor.YELLOW + "/clan join <clanName> " + ChatColor.WHITE + "- Request to join a clan (or join immediately if the clan is open).");
        player.sendMessage(ChatColor.YELLOW + "/clan kick <player> " + ChatColor.WHITE + "- Kick a member from your clan (leader only).");
        player.sendMessage(ChatColor.YELLOW + "/clan leave " + ChatColor.WHITE + "- Leave your current clan.");
        player.sendMessage(ChatColor.YELLOW + "/clan disband " + ChatColor.WHITE + "- Disband your clan (leader or op only).");
        player.sendMessage(ChatColor.YELLOW + "/clan delete <clanName> " + ChatColor.WHITE + "- Delete any clan (operator only).");
        player.sendMessage(ChatColor.YELLOW + "/clan transfer <player> " + ChatColor.WHITE + "- Transfer leadership to another member.");
        player.sendMessage(ChatColor.YELLOW + "/clan setdesc <description> " + ChatColor.WHITE + "- Set or change your clan's description.");
        player.sendMessage(ChatColor.YELLOW + "/clan settag <newTag> " + ChatColor.WHITE + "- Change your clan's tag (supports color codes).");
        player.sendMessage(ChatColor.YELLOW + "/clan rename <newName> " + ChatColor.WHITE + "- Rename your clan.");
        player.sendMessage(ChatColor.YELLOW + "/clan logs [clanName] " + ChatColor.WHITE + "- View clan logs (your own if leader; ops can view any).");
        player.sendMessage(ChatColor.YELLOW + "/clan points " + ChatColor.WHITE + "- View your clan's current points.");
        player.sendMessage(ChatColor.YELLOW + "/clan admin addpoints <clanName> <amount> " + ChatColor.WHITE + "- (Op only) Add points to a clan.");
        player.sendMessage(ChatColor.YELLOW + "/clan setrelation <otherClanName> <friend|enemy|neutral> " + ChatColor.WHITE + "- Set your clan's relationship with another clan.");
        player.sendMessage(ChatColor.YELLOW + "/clan friendaccept <fromClanName> " + ChatColor.WHITE + "- Accept a pending friend request from another clan.");
        player.sendMessage(ChatColor.YELLOW + "/clan sharepoints <otherClanName> <amount> " + ChatColor.WHITE + "- Share some of your clan's points with a friendly clan.");
        player.sendMessage(ChatColor.YELLOW + "/clan quest <list|complete|reset> [questID/resetType] " + ChatColor.WHITE + "- List available quests, complete one, or (op only) reset quests. For quests that require items, you'll need to have them in your inventory. Auto quests (e.g. killing mobs or traveling) are tracked automatically.");
        player.sendMessage(ChatColor.YELLOW + "/clan shop " + ChatColor.WHITE + "- View the clan shop and available upgrades (available if your clan has at least 2 members).");
        player.sendMessage(ChatColor.YELLOW + "/clan chest " + ChatColor.WHITE + "- Open your clan's shared chest.");
        player.sendMessage(ChatColor.YELLOW + "/clan sethome " + ChatColor.WHITE + "- Set your clan's home location (leader only, requires upgrade).");
        player.sendMessage(ChatColor.YELLOW + "/clan home " + ChatColor.WHITE + "- Teleport to your clan's home (cannot be used immediately after PvP).");
        player.sendMessage(ChatColor.YELLOW + "/clan debug <4|normal> [clanName] " + ChatColor.WHITE + "- (Op only) Toggle debug override for a clan.");
        player.sendMessage(ChatColor.YELLOW + "/clan open " + ChatColor.WHITE + "- Open your clan for public joining.");
        player.sendMessage(ChatColor.YELLOW + "/clan close " + ChatColor.WHITE + "- Close your clan (stop accepting join requests).");
        player.sendMessage(ChatColor.GOLD + "For color formatting info, use /clans help color");
    }
}
