package com.yourdomain.goonclansengine;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class GoonClansEngine extends JavaPlugin implements Listener, TabCompleter {

    public static final int MAX_TAG_LENGTH = 16;
    public static final int MAX_CLAN_NAME_LENGTH = 20;
    public static final long INVITE_COOLDOWN_MS = 10000;
    public static final long JOIN_REQUEST_COOLDOWN_MS = 10000;
    public static final long PVP_PROTECTION_MS = 10000;

    private final Map<String, Clan> clans = new HashMap<>();
    private File clansFile;
    private FileConfiguration clansConfig;

    private final Map<UUID, Long> inviteCooldown = new HashMap<>();
    private final Map<UUID, Long> joinRequestCooldown = new HashMap<>();

    // Pending friend requests: key = target clan name, value = список запросов
    private static final Map<String, List<PendingRelationRequest>> pendingFriendRequests = new HashMap<>();

    // Новые мапы для хранения кланового хома и отслеживания времени боя (PvP)
    private final Map<String, Location> clanHomes = new HashMap<>();
    private final Map<UUID, Long> lastCombat = new HashMap<>();

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
                player.sendMessage(ChatColor.RED + "Usage: /clan <create|invite|accept|decline|join|kick|leave|disband|delete|transfer|setdesc|settag|rename|logs|points|admin|setrelation|friendaccept|shop|chest|sethome|home|debug|open|close>");
                return true;
            }
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "create":
                    // Требуется ровно 3 аргумента: create, tag, name (каждый одним словом)
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
                    clan.setPoints(0); // Новые кланы начинаются с 0 поинтов
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
                            targetOnline.sendMessage(ChatColor.AQUA + "You have been invited to join clan " 
                                + playerClan.getFormattedTag() + " " + playerClan.getName() 
                                + ". Use /clan accept " + playerClan.getName() + " to join.");
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
                    player.sendMessage(ChatColor.GREEN + "You have joined clan " 
                            + inviteClan.getFormattedTag() + " " + inviteClan.getName());
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
                        leader.sendMessage(ChatColor.AQUA + player.getName() 
                                + " has requested to join your clan. Use /clan invite " 
                                + player.getName() + " to invite.");
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
                        Clan.Relationship relation = relationStr.equals("enemy") ? Clan.Relationship.ENEMY : Clan.Relationship.NEUTRAL;
                        myClanRel.setRelationship(otherClan.getName(), relation);
                        otherClan.setRelationship(myClanRel.getName(), relation);
                        myClanRel.addLog("Relation with " + otherClan.getName() + " set to " + relation);
                        otherClan.addLog("Relation with " + myClanRel.getName() + " set to " + relation);
                        player.sendMessage(ChatColor.GREEN + "Relationship set to " + relation + " with clan " + otherClan.getName());
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
                    requests.remove(foundReq);
                    if (requests.isEmpty()) {
                        pendingFriendRequests.remove(myClanFriend.getName());
                    } else {
                        pendingFriendRequests.put(myClanFriend.getName(), requests);
                    }
                    saveClans();
                    break;
                case "shop":
                    {
                        Clan shopClan = getPlayerClan(player.getUniqueId());
                        if (shopClan == null) {
                            player.sendMessage(ChatColor.RED + "You are not in a clan.");
                            return true;
                        }
                        if (!shopClan.isDebugOverride() && shopClan.getMemberCount() < 4) {
                            player.sendMessage(ChatColor.RED + "Clan shop is available only for clans with at least 4 members.");
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
                if (args.length > 1 && args[1].equalsIgnoreCase("color")) {
                    displayColorHelp(player);
                } else {
                    sendHelp(player);
                }
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
        player.sendMessage(ChatColor.GOLD + "Next upgrade: " + ChatColor.YELLOW + upgradeName + ChatColor.GOLD + " for " + cost + " points.");
        player.sendMessage(ChatColor.GREEN + "To confirm purchase, type /clan shop upgrade confirm");
    }

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
            player.sendMessage(ChatColor.RED + "Your clan does not have enough points.");
            return;
        }
        clan.subtractPoints(cost);
        if (upgradeName.equals("Shared Chest Upgrade")) {
            clan.setSharedChestPurchased(true);
            clan.addLog(player.getName() + " purchased Shared Chest Upgrade.");
            player.sendMessage(ChatColor.GREEN + "Shared Chest Upgrade purchased.");
        } else if (upgradeName.equals("Double Chest Capacity Upgrade")) {
            clan.setDoubleChest(true);
            clan.addLog(player.getName() + " purchased Double Chest Capacity Upgrade.");
            player.sendMessage(ChatColor.GREEN + "Double Chest Capacity Upgrade purchased.");
        } else if (upgradeName.equals("Clan Home Teleport Upgrade")) {
            clan.setHome(true);
            clan.addLog(player.getName() + " purchased Clan Home Teleport Upgrade.");
            player.sendMessage(ChatColor.GREEN + "Clan Home Teleport Upgrade purchased.");
        } else if (upgradeName.equals("Increase Member Limit Upgrade")) {
            clan.setExtraSlots(true);
            clan.setMaxMembers(8);
            clan.addLog(player.getName() + " purchased Increase Member Limit Upgrade.");
            player.sendMessage(ChatColor.GREEN + "Increase Member Limit Upgrade purchased. Maximum members increased to 8.");
        }
        saveClans();
    }

    private void displayColorHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Color Formatting Help ===");
        player.sendMessage(ChatColor.BLACK + "Black (&0)");
        player.sendMessage(ChatColor.DARK_BLUE + "Dark-Blue (&1)");
        player.sendMessage(ChatColor.DARK_GREEN + "Dark-Green (&2)");
        player.sendMessage(ChatColor.DARK_AQUA + "Dark-Aqua (&3)");
        player.sendMessage(ChatColor.DARK_RED + "Dark-Red (&4)");
        player.sendMessage(ChatColor.DARK_PURPLE + "Dark-Purple (&5)");
        player.sendMessage(ChatColor.GOLD + "Gold (&6)");
        player.sendMessage(ChatColor.GRAY + "Gray (&7)");
        player.sendMessage(ChatColor.DARK_GRAY + "Dark-Gray (&8)");
        player.sendMessage(ChatColor.BLUE + "Blue (&9)");
        player.sendMessage(ChatColor.GREEN + "Green (&a)");
        player.sendMessage(ChatColor.AQUA + "Aqua (&b)");
        player.sendMessage(ChatColor.RED + "Red (&c)");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Light-Purple (&d)");
        player.sendMessage(ChatColor.YELLOW + "Yellow (&e)");
        player.sendMessage(ChatColor.WHITE + "White (&f)");
        player.sendMessage(ChatColor.BOLD + "Bold (&l)");
        player.sendMessage(ChatColor.ITALIC + "Italic (&o)");
        player.sendMessage(ChatColor.UNDERLINE + "Underline (&n)");
        player.sendMessage(ChatColor.STRIKETHROUGH + "Strikethrough (&m)");
        player.sendMessage(ChatColor.MAGIC + "Magic (&k)");
        player.sendMessage(ChatColor.RESET + "Reset (&r)");
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Clan Commands Help ===");
        player.sendMessage(ChatColor.YELLOW + "/clan create <tag> <name> " + ChatColor.WHITE + "- Create a clan (tag and name must be one word)");
        player.sendMessage(ChatColor.YELLOW + "/clan invite <player> " + ChatColor.WHITE + "- Invite a player (offline supported)");
        player.sendMessage(ChatColor.YELLOW + "/clan accept <clanName> " + ChatColor.WHITE + "- Accept an invitation");
        player.sendMessage(ChatColor.YELLOW + "/clan decline <clanName> " + ChatColor.WHITE + "- Decline an invitation");
        player.sendMessage(ChatColor.YELLOW + "/clan join <clanName> " + ChatColor.WHITE + "- Request to join a clan or join immediately if open");
        player.sendMessage(ChatColor.YELLOW + "/clan kick <player> " + ChatColor.WHITE + "- Kick a player");
        player.sendMessage(ChatColor.YELLOW + "/clan leave " + ChatColor.WHITE + "- Leave your clan");
        player.sendMessage(ChatColor.YELLOW + "/clan disband " + ChatColor.WHITE + "- Disband your clan (leader or op)");
        player.sendMessage(ChatColor.YELLOW + "/clan delete <clanName> " + ChatColor.WHITE + "- Delete any clan (op only)");
        player.sendMessage(ChatColor.YELLOW + "/clan transfer <player> " + ChatColor.WHITE + "- Transfer leadership (new leader gains log access)");
        player.sendMessage(ChatColor.YELLOW + "/clan setdesc <description> " + ChatColor.WHITE + "- Set clan description");
        player.sendMessage(ChatColor.YELLOW + "/clan settag <newTag> " + ChatColor.WHITE + "- Change clan tag");
        player.sendMessage(ChatColor.YELLOW + "/clan rename <newName> " + ChatColor.WHITE + "- Rename your clan");
        player.sendMessage(ChatColor.YELLOW + "/clan logs [clanName] " + ChatColor.WHITE + "- View clan logs (leaders view own, ops any)");
        player.sendMessage(ChatColor.YELLOW + "/clan points " + ChatColor.WHITE + "- View clan points");
        player.sendMessage(ChatColor.YELLOW + "/clan admin addpoints <clanName> <amount> " + ChatColor.WHITE + "- Add points to a clan (op only)");
        player.sendMessage(ChatColor.YELLOW + "/clan setrelation <otherClanName> <friend|enemy|neutral> " + ChatColor.WHITE + "- Set relationship with another clan");
        player.sendMessage(ChatColor.YELLOW + "/clan friendaccept <fromClanName> " + ChatColor.WHITE + "- Accept friend relationship request");
        player.sendMessage(ChatColor.YELLOW + "/clan shop " + ChatColor.WHITE + "- View clan shop and available upgrades");
        player.sendMessage(ChatColor.YELLOW + "/clan chest " + ChatColor.WHITE + "- Open clan shared chest");
        player.sendMessage(ChatColor.YELLOW + "/clan sethome " + ChatColor.WHITE + "- Set clan home (leader only, requires home upgrade)");
        player.sendMessage(ChatColor.YELLOW + "/clan home " + ChatColor.WHITE + "- Teleport to clan home (requires no recent PvP)");
        player.sendMessage(ChatColor.YELLOW + "/clan debug <4|normal> [clanName] " + ChatColor.WHITE + "- Debug command for ops");
        player.sendMessage(ChatColor.YELLOW + "/clan open " + ChatColor.WHITE + "- Open your clan for public joining");
        player.sendMessage(ChatColor.YELLOW + "/clan close " + ChatColor.WHITE + "- Close your clan");
        player.sendMessage(ChatColor.YELLOW + "For color formatting info, use /clans help color");
    }

    private void sendClanInfo(Player player, Clan clan) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        String created = sdf.format(new Date(clan.getCreatedAt()));
        player.sendMessage(ChatColor.GOLD + "=== Clan Info: " + clan.getName() + " ===");
        player.sendMessage(ChatColor.YELLOW + "Tag: " + ChatColor.WHITE + clan.getFormattedTag());
        player.sendMessage(ChatColor.YELLOW + "Leader: " + ChatColor.WHITE + Bukkit.getOfflinePlayer(clan.getLeader()).getName());
        player.sendMessage(ChatColor.YELLOW + "Created: " + ChatColor.WHITE + created);
        player.sendMessage(ChatColor.YELLOW + "Description: " + ChatColor.WHITE + (clan.getDescription().isEmpty() ? "None" : clan.getDescription()));
        player.sendMessage(ChatColor.YELLOW + "Points: " + ChatColor.WHITE + clan.getPoints());
        player.sendMessage(ChatColor.YELLOW + "Max Members: " + ChatColor.WHITE + clan.getMaxMembers());
        String membersList = clan.getMembers().stream().map(uuid -> Bukkit.getOfflinePlayer(uuid).getName()).collect(Collectors.joining(", "));
        player.sendMessage(ChatColor.YELLOW + "Members (" + clan.getMemberCount() + "): " + ChatColor.WHITE + membersList);
        if (!clan.getRelationships().isEmpty()) {
            String relations = clan.getRelationships().entrySet().stream().map(e -> e.getKey() + " (" + e.getValue().toString().toLowerCase() + ")").collect(Collectors.joining(", "));
            player.sendMessage(ChatColor.YELLOW + "Relations: " + ChatColor.WHITE + relations);
        }
        String status = clan.isOpen() ? ChatColor.GREEN + "Open" : ChatColor.RED + "Closed";
        player.sendMessage(ChatColor.YELLOW + "Status: " + ChatColor.WHITE + status);
        player.sendMessage(ChatColor.GOLD + "==================================");
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player))
            return;
        Player victim = (Player) event.getEntity();
        Player damager = (Player) event.getDamager();
        Clan clanVictim = getPlayerClan(victim.getUniqueId());
        Clan clanDamager = getPlayerClan(damager.getUniqueId());
        if (clanVictim != null && clanDamager != null) {
            boolean sameClan = clanVictim.equals(clanDamager);
            boolean friendlyRelation = false;
            if (clanVictim.getRelationships().containsKey(clanDamager.getName()) && clanVictim.getRelationships().get(clanDamager.getName()) == Clan.Relationship.FRIEND) {
                friendlyRelation = true;
            }
            if (clanDamager.getRelationships().containsKey(clanVictim.getName()) && clanDamager.getRelationships().get(clanVictim.getName()) == Clan.Relationship.FRIEND) {
                friendlyRelation = true;
            }
            if (sameClan || friendlyRelation) {
                event.setCancelled(true);
                damager.sendMessage(ChatColor.RED + "You cannot damage friendly clan members!");
            }
        }
        // Обновляем время последнего урона для защиты от телепортации во время PvP
        if (event.getEntity() instanceof Player) {
            lastCombat.put(victim.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Clan clan = getPlayerClan(player.getUniqueId());
        String format;
        if (clan != null) {
            format = "<" + clan.getFormattedTag() + ChatColor.RESET + "> " + ChatColor.GRAY + "[" + player.getName() + "]" + ChatColor.RESET + " > " + event.getMessage();
        } else {
            format = ChatColor.GRAY + "[" + player.getName() + "]" + ChatColor.RESET + " > " + event.getMessage();
        }
        event.setFormat(format);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        updatePlayerDisplay(player);
        Clan clan = getPlayerClan(player.getUniqueId());
        if (clan != null && clan.isLeader(player.getUniqueId())) {
            List<PendingRelationRequest> reqs = pendingFriendRequests.getOrDefault(clan.getName(), new ArrayList<>());
            if (!reqs.isEmpty()) {
                player.sendMessage(ChatColor.AQUA + "You have pending friend requests from clans: " +
                        reqs.stream().map(r -> r.fromClan).collect(Collectors.joining(", ")) +
                        ". Use /clan friendaccept <fromClanName> to accept.");
            }
        }
        // При входе уведомляем игрока об оффлайн приглашениях
        List<String> invitedClans = new ArrayList<>();
        for (Clan c : clans.values()) {
            if (c.getInvites().contains(player.getUniqueId())) {
                invitedClans.add(c.getName());
            }
        }
        if (!invitedClans.isEmpty()) {
            player.sendMessage(ChatColor.AQUA + "You have pending clan invites: " + String.join(", ", invitedClans) + ". Use /clan accept <clanName> to join or /clan decline <clanName> to decline.");
        }
    }

    private Clan getPlayerClan(UUID uuid) {
        return clans.values().stream().filter(clan -> clan.getMembers().contains(uuid)).findFirst().orElse(null);
    }

    private void updatePlayerDisplay(Player player) {
        Clan clan = getPlayerClan(player.getUniqueId());
        String prefix = "";
        if (clan != null) {
            prefix = "<" + clan.getFormattedTag() + ChatColor.RESET + ">";
        }
        player.setPlayerListName(prefix + " " + player.getName());
    }

    private void loadClans() {
        clansFile = new File(getDataFolder(), "clans.yml");
        if (!clansFile.exists()) {
            clansFile.getParentFile().mkdirs();
            saveResource("clans.yml", false);
        }
        clansConfig = YamlConfiguration.loadConfiguration(clansFile);
        if (clansConfig.contains("clans")) {
            for (String key : clansConfig.getConfigurationSection("clans").getKeys(false)) {
                String name = clansConfig.getString("clans." + key + ".name");
                String tag = clansConfig.getString("clans." + key + ".tag");
                if (name == null || tag == null) {
                    getLogger().warning("Skipping clan entry '" + key + "' due to missing name or tag.");
                    continue;
                }
                UUID leader = UUID.fromString(clansConfig.getString("clans." + key + ".leader"));
                String description = clansConfig.getString("clans." + key + ".description", "");
                long createdAt = clansConfig.getLong("clans." + key + ".createdAt");
                Clan clan = new Clan(name, tag, leader, createdAt);
                List<String> memberList = clansConfig.getStringList("clans." + key + ".members");
                for (String s : memberList) {
                    clan.addMember(UUID.fromString(s));
                }
                List<String> inviteList = clansConfig.getStringList("clans." + key + ".invites");
                for (String s : inviteList) {
                    clan.addInvite(UUID.fromString(s));
                }
                List<String> joinReqList = clansConfig.getStringList("clans." + key + ".joinRequests");
                for (String s : joinReqList) {
                    clan.addJoinRequest(UUID.fromString(s));
                }
                clan.setPoints(clansConfig.getInt("clans." + key + ".points", 0));
                clan.setSharedChestPurchased(clansConfig.getBoolean("clans." + key + ".upgrades.sharedChest", false));
                clan.setDoubleChest(clansConfig.getBoolean("clans." + key + ".upgrades.doubleChest", false));
                clan.setHome(clansConfig.getBoolean("clans." + key + ".upgrades.home", false));
                clan.setExtraSlots(clansConfig.getBoolean("clans." + key + ".upgrades.extraSlots", false));
                clan.setMaxMembers(clansConfig.getInt("clans." + key + ".maxMembers", 4));
                if (clansConfig.contains("clans." + key + ".relationships")) {
                    for (String otherClan : clansConfig.getConfigurationSection("clans." + key + ".relationships").getKeys(false)) {
                        String relStr = clansConfig.getString("clans." + key + ".relationships." + otherClan);
                        Clan.Relationship rel = Clan.Relationship.valueOf(relStr.toUpperCase());
                        clan.setRelationship(otherClan, rel);
                    }
                }
                clan.setDebugOverride(clansConfig.getBoolean("clans." + key + ".debugOverride", false));
                clan.setOpen(clansConfig.getBoolean("clans." + key + ".open", false));
                clans.put(name, clan);
                if (clansConfig.contains("clans." + key + ".clanHome")) {
                    String locStr = clansConfig.getString("clans." + key + ".clanHome");
                    Location homeLoc = deserializeLocation(locStr);
                    if (homeLoc != null) {
                        clanHomes.put(name, homeLoc);
                    }
                }
            }
        }
    }

    private void saveClans() {
        if (clansConfig == null) {
            clansConfig = new YamlConfiguration();
        }
        clansConfig.set("clans", null);
        for (Clan clan : clans.values()) {
            String path = "clans." + clan.getName();
            clansConfig.set(path + ".name", clan.getName());
            clansConfig.set(path + ".tag", clan.getTag());
            clansConfig.set(path + ".leader", clan.getLeader().toString());
            clansConfig.set(path + ".description", clan.getDescription());
            clansConfig.set(path + ".createdAt", clan.getCreatedAt());
            clansConfig.set(path + ".members", clan.getMembers().stream().map(UUID::toString).collect(Collectors.toList()));
            clansConfig.set(path + ".invites", clan.getInvites().stream().map(UUID::toString).collect(Collectors.toList()));
            clansConfig.set(path + ".joinRequests", clan.getJoinRequests().stream().map(UUID::toString).collect(Collectors.toList()));
            clansConfig.set(path + ".points", clan.getPoints());
            clansConfig.set(path + ".maxMembers", clan.getMaxMembers());
            clansConfig.set(path + ".upgrades.sharedChest", clan.hasSharedChest());
            clansConfig.set(path + ".upgrades.doubleChest", clan.hasDoubleChest());
            clansConfig.set(path + ".upgrades.home", clan.hasHome());
            clansConfig.set(path + ".upgrades.extraSlots", clan.hasExtraSlots());
            clansConfig.set(path + ".debugOverride", clan.isDebugOverride());
            clansConfig.set(path + ".open", clan.isOpen());
            for (Map.Entry<String, Clan.Relationship> entry : clan.getRelationships().entrySet()) {
                clansConfig.set(path + ".relationships." + entry.getKey(), entry.getValue().toString());
            }
            if (clanHomes.containsKey(clan.getName())) {
                clansConfig.set(path + ".clanHome", serializeLocation(clanHomes.get(clan.getName())));
            }
        }
        try {
            clansConfig.save(clansFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String serializeLocation(Location loc) {
        if (loc == null) return "";
        return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch();
    }

    private Location deserializeLocation(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] parts = s.split(",");
        if (parts.length != 6) return null;
        String worldName = parts[0];
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = Float.parseFloat(parts[4]);
            float pitch = Float.parseFloat(parts[5]);
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player))
            return Collections.emptyList();
        Player player = (Player) sender;
        List<String> completions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("clan")) {
            if (args.length == 1) {
                List<String> subs = Arrays.asList("create", "invite", "accept", "decline", "join", "kick", "leave", "disband", "delete", "transfer", "setdesc", "settag", "rename", "logs", "points", "admin", "setrelation", "friendaccept", "shop", "chest", "sethome", "home", "debug", "open", "close");
                String current = args[0].toLowerCase();
                completions.addAll(subs.stream().filter(s -> s.startsWith(current)).collect(Collectors.toList()));
            } else if (args.length == 2) {
                String subCommand = args[0].toLowerCase();
                switch (subCommand) {
                    case "invite":
                        completions.addAll(Bukkit.getOnlinePlayers().stream()
                                .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                                .filter(p -> getPlayerClan(p.getUniqueId()) == null)
                                .map(Player::getName)
                                .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList()));
                        break;
                    case "kick":
                    case "transfer":
                        Clan clan = getPlayerClan(player.getUniqueId());
                        if (clan != null) {
                            completions.addAll(clan.getMembers().stream()
                                    .map(uuid -> Bukkit.getPlayer(uuid))
                                    .filter(Objects::nonNull)
                                    .map(Player::getName)
                                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                                    .collect(Collectors.toList()));
                        }
                        break;
                    case "join":
                    case "accept":
                    case "decline":
                        completions.addAll(clans.keySet().stream()
                                .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList()));
                        break;
                    case "logs":
                        if (player.isOp()) {
                            completions.addAll(clans.keySet().stream()
                                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                                    .collect(Collectors.toList()));
                        }
                        break;
                    case "setrelation":
                        Clan playerClan = getPlayerClan(player.getUniqueId());
                        if (playerClan != null) {
                            completions.addAll(clans.keySet().stream()
                                    .filter(name -> !name.equalsIgnoreCase(playerClan.getName()))
                                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                                    .collect(Collectors.toList()));
                        }
                        break;
                    case "admin":
                        if ("addpoints".startsWith(args[1].toLowerCase())) {
                            completions.add("addpoints");
                        }
                        break;
                    case "friendaccept":
                        Clan myClan = getPlayerClan(player.getUniqueId());
                        if (myClan != null) {
                            List<PendingRelationRequest> reqs = pendingFriendRequests.getOrDefault(myClan.getName(), new ArrayList<>());
                            completions.addAll(reqs.stream().map(req -> req.fromClan).collect(Collectors.toList()));
                        }
                        break;
                    case "shop":
                        completions.add("upgrade");
                        break;
                }
            } else if (args.length == 3) {
                String subCommand = args[0].toLowerCase();
                if (subCommand.equals("debug")) {
                    completions.addAll(clans.keySet().stream()
                            .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList()));
                }
            }
        } else if (command.getName().equalsIgnoreCase("clans")) {
            if (args.length == 1) {
                List<String> subs = Arrays.asList("help", "info");
                String current = args[0].toLowerCase();
                completions.addAll(subs.stream().filter(s -> s.startsWith(current)).collect(Collectors.toList()));
            } else if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
                completions.addAll(clans.keySet().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList()));
            } else if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
                completions.add("color");
            }
        }
        return completions;
    }
}
