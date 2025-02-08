package com.yourdomain.goonclansengine;

import org.bukkit.inventory.Inventory;
import java.util.*;

public class Clan {
    public enum Relationship {
        FRIEND, ENEMY, NEUTRAL
    }

    private String name;
    private String tag;
    private UUID leader;
    private String description = "";
    private long createdAt;
    private Set<UUID> members = new HashSet<>();
    private Set<UUID> invites = new HashSet<>();
    private Set<UUID> joinRequests = new HashSet<>();
    private int points = 0;
    private int maxMembers = 4;
    private boolean sharedChestPurchased = false;
    private boolean doubleChest = false;
    private boolean home = false;
    private boolean extraSlots = false;
    private Inventory sharedChest = null;
    private List<String> logs = new ArrayList<>();
    private Map<String, Relationship> relationships = new HashMap<>();

    // Новые поля
    private boolean debugOverride = false;
    private boolean open = false;

    public Clan(String name, String tag, UUID leader) {
        this.name = name;
        this.tag = tag;
        this.leader = leader;
        this.createdAt = System.currentTimeMillis();
    }

    public Clan(String name, String tag, UUID leader, long createdAt) {
        this.name = name;
        this.tag = tag;
        this.leader = leader;
        this.createdAt = createdAt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public Set<UUID> getInvites() {
        return invites;
    }

    public void addInvite(UUID uuid) {
        invites.add(uuid);
    }

    public void removeInvite(UUID uuid) {
        invites.remove(uuid);
    }

    public Set<UUID> getJoinRequests() {
        return joinRequests;
    }

    public void addJoinRequest(UUID uuid) {
        joinRequests.add(uuid);
    }

    public void removeJoinRequest(UUID uuid) {
        joinRequests.remove(uuid);
    }

    public int getPoints() {
        return points;
    }

    public void addPoints(int pts) {
        points += pts;
    }

    public void subtractPoints(int pts) {
        points -= pts;
    }

    public void setPoints(int pts) {
        points = pts;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public void setMaxMembers(int maxMembers) {
        this.maxMembers = maxMembers;
    }

    public boolean hasSharedChest() {
        return sharedChestPurchased;
    }

    public void setSharedChestPurchased(boolean sharedChestPurchased) {
        this.sharedChestPurchased = sharedChestPurchased;
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

    public Inventory getSharedChest() {
        return sharedChest;
    }

    public void setSharedChest(Inventory sharedChest) {
        this.sharedChest = sharedChest;
    }

    public List<String> getLogs() {
        return logs;
    }

    public void addLog(String log) {
        logs.add("[" + new Date() + "] " + log);
    }

    public Map<String, Relationship> getRelationships() {
        return relationships;
    }

    public void setRelationship(String clanName, Relationship relationship) {
        relationships.put(clanName, relationship);
    }

    public boolean isLeader(UUID uuid) {
        return leader.equals(uuid);
    }

    public String getFormattedTag() {
        return tag;
    }

    // Методы для debug override
    public boolean isDebugOverride() {
        return debugOverride;
    }

    public void setDebugOverride(boolean debugOverride) {
        this.debugOverride = debugOverride;
    }

    // Методы для открытия клана
    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }
}
