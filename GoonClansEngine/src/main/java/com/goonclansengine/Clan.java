package com.yourdomain.goonclansengine;

import org.bukkit.ChatColor;
import org.bukkit.inventory.Inventory;

import java.text.SimpleDateFormat;
import java.util.*;

public class Clan {

    public enum Relationship {
        FRIEND, ENEMY, NEUTRAL
    }

    private String name;
    private String tag;
    private String description;
    private UUID leader;
    private long createdAt;
    private final Set<UUID> members = new HashSet<>();
    private final Set<UUID> invites = new HashSet<>();
    private final Set<UUID> joinRequests = new HashSet<>();
    private int points = 0;
    private final Map<String, Relationship> relationships = new HashMap<>();
    private final List<String> logs = new ArrayList<>();

    // Upgrades
    private boolean sharedChestPurchased = false;
    private boolean doubleChest = false;
    private boolean home = false;
    private boolean extraSlots = false;

    // Maximum members (default 4, can be increased to 8)
    private int maxMembers;

    private Inventory sharedChest = null;

    // Добавлены проверки, чтобы name и tag не были null
    public Clan(String name, String tag, UUID leader) {
        if (name == null || tag == null) {
            throw new IllegalArgumentException("Name and tag must not be null.");
        }
        this.name = name;
        this.tag = tag;
        this.leader = leader;
        this.createdAt = System.currentTimeMillis();
        this.description = "";
        this.maxMembers = 4;
    }

    public Clan(String name, String tag, UUID leader, long createdAt) {
        if (name == null || tag == null) {
            throw new IllegalArgumentException("Name and tag must not be null.");
        }
        this.name = name;
        this.tag = tag;
        this.leader = leader;
        this.createdAt = createdAt;
        this.description = "";
        this.maxMembers = 4;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Clan name must not be null or empty.");
        }
        this.name = name;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        if (tag == null) {
            throw new IllegalArgumentException("Clan tag must not be null.");
        }
        this.tag = tag;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public int getMemberCount() {
        return members.size();
    }

    public Set<UUID> getInvites() {
        return invites;
    }

    public Set<UUID> getJoinRequests() {
        return joinRequests;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public void addPoints(int amount) {
        this.points += amount;
    }

    public void subtractPoints(int amount) {
        this.points -= amount;
    }

    public Map<String, Relationship> getRelationships() {
        return relationships;
    }

    public List<String> getLogs() {
        return logs;
    }

    public boolean hasSharedChest() {
        return sharedChestPurchased;
    }

    public void setSharedChestPurchased(boolean purchased) {
        this.sharedChestPurchased = purchased;
    }

    public boolean hasDoubleChest() {
        return doubleChest;
    }

    public void setDoubleChest(boolean doubleChest) {
        this.doubleChest = doubleChest;
    }

    public boolean hasHome() {
        return home;
    }

    public void setHome(boolean home) {
        this.home = home;
    }

    public boolean hasExtraSlots() {
        return extraSlots;
    }

    public void setExtraSlots(boolean extraSlots) {
        this.extraSlots = extraSlots;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public void setMaxMembers(int maxMembers) {
        this.maxMembers = maxMembers;
    }

    public Inventory getSharedChest() {
        return sharedChest;
    }

    public void setSharedChest(Inventory inv) {
        this.sharedChest = inv;
    }

    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean isLeader(UUID uuid) {
        return leader.equals(uuid);
    }

    public void addInvite(UUID uuid) {
        invites.add(uuid);
    }

    public void removeInvite(UUID uuid) {
        invites.remove(uuid);
    }

    public void addJoinRequest(UUID uuid) {
        joinRequests.add(uuid);
    }

    public void removeJoinRequest(UUID uuid) {
        joinRequests.remove(uuid);
    }

    public void setRelationship(String otherClanName, Relationship relation) {
        relationships.put(otherClanName, relation);
    }

    public void addLog(String action) {
        String timestamp = new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date());
        logs.add("[" + timestamp + "] " + action);
    }

    public String getFormattedTag() {
        // Здесь можно добавить дополнительное форматирование тега (например, с цветами)
        return tag;
    }
}
