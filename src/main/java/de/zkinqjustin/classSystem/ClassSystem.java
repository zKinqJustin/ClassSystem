package de.zkinqjustin.classSystem;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import java.sql.*;
import java.util.HashMap;
import java.util.UUID;
import java.util.Map;

public class ClassSystem extends JavaPlugin implements Listener {

    private Connection connection;
    private String host, database, username, password;
    private int port;
    private HashMap<UUID, Integer> manaMap = new HashMap<>();
    private Map<String, Integer> mobExpValues = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadDatabaseConfig();
        loadMobExpValues();
        connectToDatabase();
        createTable();

        getServer().getPluginManager().registerEvents(this, this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ClassPlaceholders(this).register();
        }

        startManaRegenTask();
    }

    @Override
    public void onDisable() {
        closeConnection();
    }

    private void loadDatabaseConfig() {
        host = getConfig().getString("database.host");
        port = getConfig().getInt("database.port");
        database = getConfig().getString("database.database");
        username = getConfig().getString("database.username");
        password = getConfig().getString("database.password");
    }

    private void loadMobExpValues() {
        if (getConfig().isConfigurationSection("mob-exp")) {
            for (String key : getConfig().getConfigurationSection("mob-exp").getKeys(false)) {
                int expValue = getConfig().getInt("mob-exp." + key);
                mobExpValues.put(key.toLowerCase(), expValue);
            }
        }
    }

    private void connectToDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database, username, password);
            getLogger().info("Successfully connected to the database!");
        } catch (SQLException e) {
            getLogger().severe("Failed to connect to the database: " + e.getMessage());
        }
    }

    private void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            getLogger().severe("Error closing database connection: " + e.getMessage());
        }
    }

    private void createTable() {
        try (Statement stmt = connection.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS player_classes (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "class VARCHAR(20) NOT NULL, " +
                    "level INT NOT NULL, " +
                    "exp INT NOT NULL)";
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            getLogger().severe("Error creating table: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!playerExists(uuid)) {
            setPlayerClass(player, "None");
        }
        updateManaBar(player);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() instanceof Player) {
            Player killer = event.getEntity().getKiller();
            LivingEntity entity = event.getEntity();

            String mobType = getMobType(entity);

            if (mobExpValues.containsKey(mobType)) {
                int expToAdd = mobExpValues.get(mobType);
                addExp(killer, expToAdd);
                killer.sendMessage(ChatColor.GREEN + "You gained " + expToAdd + " EXP for killing a " + mobType + "!");
            }
        }
    }

    private String getMobType(LivingEntity entity) {
        // Check for custom mob type (e.g., MythicMobs)
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(this, "mythicmobs_type");
        String customType = pdc.get(key, PersistentDataType.STRING);

        if (customType != null) {
            return customType.toLowerCase();
        }

        // If not a custom mob, return the vanilla mob type
        return entity.getType().name().toLowerCase();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("klasse")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                openClassSelectionGUI(player);
            } else {
                sender.sendMessage("This command can only be used by players.");
            }
            return true;
        } else if (cmd.getName().equalsIgnoreCase("exp")) {
            if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) {
                    try {
                        int amount = Integer.parseInt(args[2]);
                        addExp(target, amount);
                        sender.sendMessage("Added " + amount + " exp to " + target.getName());
                    } catch (NumberFormatException e) {
                        sender.sendMessage("Invalid exp amount.");
                    }
                } else {
                    sender.sendMessage("Player not found.");
                }
            } else {
                sender.sendMessage("Usage: /exp add <player> <amount>");
            }
            return true;
        }
        return false;
    }

    private void openClassSelectionGUI(Player player) {
        UUID uuid = player.getUniqueId();
        if (!getPlayerClass(player).equals("None")) {
            player.sendMessage(ChatColor.RED + "You have already chosen a class!");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 9, "Choose Your Class");

        gui.setItem(0, createClassItem(Material.IRON_SWORD, "Warrior"));
        gui.setItem(2, createClassItem(Material.BLAZE_ROD, "Mage"));
        gui.setItem(4, createClassItem(Material.GOLDEN_APPLE, "Healer"));
        gui.setItem(6, createClassItem(Material.IRON_AXE, "Dwarf"));
        gui.setItem(8, createClassItem(Material.OAK_SAPLING, "Druid"));

        player.openInventory(gui);
    }

    private ItemStack createClassItem(Material material, String className) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + className);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("Choose Your Class")) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        String className = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        setPlayerClass(player, className);
        player.closeInventory();
    }

    private boolean playerExists(UUID uuid) {
        try (PreparedStatement pstmt = connection.prepareStatement("SELECT * FROM player_classes WHERE uuid = ?")) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            getLogger().severe("Error checking if player exists: " + e.getMessage());
            return false;
        }
    }

    private void setPlayerClass(Player player, String className) {
        UUID uuid = player.getUniqueId();
        try (PreparedStatement pstmt = connection.prepareStatement(
                "INSERT INTO player_classes (uuid, class, level, exp) VALUES (?, ?, 1, 0) " +
                        "ON DUPLICATE KEY UPDATE class = ?")) {
            pstmt.setString(1, uuid.toString());
            pstmt.setString(2, className);
            pstmt.setString(3, className);
            pstmt.executeUpdate();
            player.sendMessage(ChatColor.GREEN + "You have chosen the " + className + " class!");
        } catch (SQLException e) {
            getLogger().severe("Error setting player class: " + e.getMessage());
        }
    }

    private void addExp(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        try (PreparedStatement pstmt = connection.prepareStatement(
                "UPDATE player_classes SET exp = exp + ?, level = ? WHERE uuid = ?")) {
            int currentExp = getPlayerExp(player) + amount;
            int newLevel = calculateLevel(currentExp);
            pstmt.setInt(1, amount);
            pstmt.setInt(2, newLevel);
            pstmt.setString(3, uuid.toString());
            pstmt.executeUpdate();

            if (newLevel > getPlayerLevel(player)) {
                player.sendMessage(ChatColor.GREEN + "Congratulations! You've reached level " + newLevel + "!");
            }
        } catch (SQLException e) {
            getLogger().severe("Error adding exp: " + e.getMessage());
        }
    }

    private int calculateLevel(int exp) {
        // Simple level calculation, can be adjusted as needed
        return (int) Math.floor(Math.sqrt(exp / 100)) + 1;
    }

    private void updateManaBar(Player player) {
        UUID uuid = player.getUniqueId();
        int mana = manaMap.getOrDefault(uuid, 100);
        player.setExp((float) mana / 100);
    }

    private void startManaRegenTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    int currentMana = manaMap.getOrDefault(uuid, 100);
                    if (currentMana < 100) {
                        manaMap.put(uuid, Math.min(currentMana + 5, 100));
                        updateManaBar(player);
                    }
                }
            }
        }.runTaskTimer(this, 100L, 100L); // Regenerate 5 mana every 5 seconds
    }

    public String getPlayerClass(Player player) {
        try (PreparedStatement pstmt = connection.prepareStatement("SELECT class FROM player_classes WHERE uuid = ?")) {
            pstmt.setString(1, player.getUniqueId().toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("class");
            }
        } catch (SQLException e) {
            getLogger().severe("Error getting player class: " + e.getMessage());
        }
        return "None";
    }

    public int getPlayerLevel(Player player) {
        try (PreparedStatement pstmt = connection.prepareStatement("SELECT level FROM player_classes WHERE uuid = ?")) {
            pstmt.setString(1, player.getUniqueId().toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("level");
            }
        } catch (SQLException e) {
            getLogger().severe("Error getting player level: " + e.getMessage());
        }
        return 1;
    }

    private int getPlayerExp(Player player) {
        try (PreparedStatement pstmt = connection.prepareStatement("SELECT exp FROM player_classes WHERE uuid = ?")) {
            pstmt.setString(1, player.getUniqueId().toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("exp");
            }
        } catch (SQLException e) {
            getLogger().severe("Error getting player exp: " + e.getMessage());
        }
        return 0;
    }

    public int getPlayerMana(Player player) {
        return manaMap.getOrDefault(player.getUniqueId(), 100);
    }

    public void useMana(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        int currentMana = manaMap.getOrDefault(uuid, 100);
        manaMap.put(uuid, Math.max(currentMana - amount, 0));
        updateManaBar(player);
    }
}

