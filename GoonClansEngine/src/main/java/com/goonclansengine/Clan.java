package com.yourdomain.goonclansengine;

import org.bukkit.ChatColor;
import org.bukkit.inventory.Inventory;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Класс Clan реализует всю необходимую функциональность для работы с кланами:
 * - название, тег, описание;
 * - лидер, участники, приглашения и запросы на вступление;
 * - очки, журнал событий, максимальное количество участников;
 * - настройки магазина (апгрейды sharedChest, doubleChest, home, extraSlots);
 * - отношения с другими кланами и debug override.
 */
public class Clan {

    private String name;
    private String tag;
    private UUID leader;
    private Set<UUID> members;
    private Set<UUID> invites;
    private Set<UUID> joinRequests;
    private int maxMembers;
    private int points;
    private List<String> logs;
    private String description;
    private boolean open;
    private Map<String, Relationship> relationships;
    private boolean debugOverride;

    // Настройки апгрейдов магазина
    private boolean hasSharedChest;
    private boolean hasDoubleChest;
    private boolean hasHome;
    private boolean extraSlots;
    private Inventory sharedChest;

    /**
     * Тип отношений между кланами.
     */
    public enum Relationship {
        FRIEND,
        ENEMY,
        NEUTRAL
    }

    /**
     * Конструктор клана.
     *
     * @param name   Название клана.
     * @param tag    Тег клана (с возможными цветовыми кодами).
     * @param leader UUID лидера клана.
     */
    public Clan(String name, String tag, UUID leader) {
        this.name = name;
        this.tag = tag;
        this.leader = leader;
        this.members = new HashSet<>();
        this.invites = new HashSet<>();
        this.joinRequests = new HashSet<>();
        this.maxMembers = 6; // По умолчанию 6 участников
        this.points = 0;
        this.logs = new ArrayList<>();
        this.description = "";
        this.open = false;
        this.relationships = new HashMap<>();
        this.debugOverride = false;
        this.hasSharedChest = false;
        this.hasDoubleChest = false;
        this.hasHome = false;
        this.extraSlots = false;
        this.sharedChest = null;
    }

    // Геттеры и сеттеры

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTag() {
        return tag;
    }

    /**
     * Возвращает тег клана с применёнными цветовыми кодами.
     *
     * @return отформатированный тег.
     */
    public String getFormattedTag() {
        return ChatColor.translateAlternateColorCodes('&', tag);
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

    public Set<UUID> getMembers() {
        return members;
    }

    /**
     * Возвращает текущее количество участников клана.
     *
     * @return количество участников.
     */
    public int getMemberCount() {
        return members.size();
    }

    /**
     * Добавляет участника в клан.
     *
     * @param uuid UUID игрока.
     */
    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    /**
     * Удаляет участника из клана. Если удалённый игрок является лидером, лидерство автоматически передаётся.
     *
     * @param uuid UUID игрока.
     */
    public void removeMember(UUID uuid) {
        members.remove(uuid);
        if (uuid.equals(leader) && !members.isEmpty()) {
            // Передаём лидерство первому попавшемуся участнику
            leader = members.iterator().next();
            addLog("Leadership automatically transferred to " + leader.toString());
        }
    }

    /**
     * Проверяет, является ли переданный UUID лидером клана.
     *
     * @param uuid UUID игрока.
     * @return true, если является лидером.
     */
    public boolean isLeader(UUID uuid) {
        return leader.equals(uuid);
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public void setMaxMembers(int maxMembers) {
        this.maxMembers = maxMembers;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    /**
     * Добавляет указанное количество очков к клану.
     *
     * @param amount Количество очков.
     */
    public void addPoints(int amount) {
        this.points += amount;
    }

    /**
     * Вычитает указанное количество очков из клана.
     *
     * @param amount Количество очков.
     */
    public void subtractPoints(int amount) {
        this.points -= amount;
    }

    public List<String> getLogs() {
        return logs;
    }

    /**
     * Добавляет запись в журнал клана с отметкой времени.
     *
     * @param log Сообщение для журнала.
     */
    public void addLog(String log) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        logs.add("[" + timestamp + "] " + log);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public Map<String, Relationship> getRelationships() {
        return relationships;
    }

    /**
     * Устанавливает отношение с другим кланом.
     *
     * @param clanName     Название другого клана.
     * @param relationship Тип отношения.
     */
    public void setRelationship(String clanName, Relationship relationship) {
        relationships.put(clanName, relationship);
    }

    public boolean isDebugOverride() {
        return debugOverride;
    }

    public void setDebugOverride(boolean debugOverride) {
        this.debugOverride = debugOverride;
    }

    // Методы для работы с апгрейдами магазина

    public boolean hasSharedChest() {
        return hasSharedChest;
    }

    public void setHasSharedChest(boolean hasSharedChest) {
        this.hasSharedChest = hasSharedChest;
    }

    public boolean hasDoubleChest() {
        return hasDoubleChest;
    }

    public void setHasDoubleChest(boolean hasDoubleChest) {
        this.hasDoubleChest = hasDoubleChest;
    }

    public boolean hasHome() {
        return hasHome;
    }

    public void setHasHome(boolean hasHome) {
        this.hasHome = hasHome;
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

    // Методы для работы с приглашениями и запросами

    /**
     * Возвращает множество UUID приглашённых игроков.
     *
     * @return приглашения.
     */
    public Set<UUID> getInvites() {
        return invites;
    }

    /**
     * Добавляет приглашение игроку.
     *
     * @param uuid UUID игрока.
     */
    public void addInvite(UUID uuid) {
        invites.add(uuid);
    }

    /**
     * Удаляет приглашение для игрока.
     *
     * @param uuid UUID игрока.
     */
    public void removeInvite(UUID uuid) {
        invites.remove(uuid);
    }

    /**
     * Возвращает множество запросов на вступление в клан.
     *
     * @return запросы.
     */
    public Set<UUID> getJoinRequests() {
        return joinRequests;
    }

    /**
     * Добавляет запрос на вступление в клан.
     *
     * @param uuid UUID игрока.
     */
    public void addJoinRequest(UUID uuid) {
        joinRequests.add(uuid);
    }

    /**
     * Удаляет запрос на вступление.
     *
     * @param uuid UUID игрока.
     */
    public void removeJoinRequest(UUID uuid) {
        joinRequests.remove(uuid);
    }
}
