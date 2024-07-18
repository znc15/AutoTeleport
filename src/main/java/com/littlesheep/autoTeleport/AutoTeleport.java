package com.littlesheep.autoTeleport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.milkbowl.vault.economy.Economy;
import net.md_5.bungee.api.ChatColor;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoTeleport extends JavaPlugin implements Listener {

    private final Map<Player, Player> tpaRequests = new HashMap<>();
    private final Random random = new Random();
    private FileConfiguration langConfig;
    private BossBar bossBar;
    private final Map<UUID, Long> teleportCooldowns = new HashMap<>();
    private Economy economy;
    private final Map<Player, BukkitRunnable> autoTeleportTasks = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureLangFiles();
        loadLanguageFile();
        // 输出启动信息
        getLogger().info("==========================================");
        getLogger().info(getDescription().getName());
        getLogger().info("Version/版本: " + getDescription().getVersion());
        getLogger().info("Author/作者: " + String.join(", ", getDescription().getAuthors()));
        getLogger().info("QQ Group/QQ群: 690216634");
        getLogger().info("Github: https://github.com/znc15/AutoTeleport");
        getLogger().info(getDescription().getName() + " 已启用！");
        getLogger().info("Ciallo～(∠・ω< )⌒★");
        getLogger().info("==========================================");

        if (getConfig().getBoolean("enable-stats", true)) {
            setupMetrics();
        }
        setupEconomy();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("AutoTeleport has been enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("AutoTeleport has been disabled");
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ensurePlayerDataFile(player);
    }

    private void ensureLangFiles() {
        File langDir = new File(getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        saveResourceIfNotExists("lang/en_US.yml");
        saveResourceIfNotExists("lang/zh_CN.yml");
    }

    private void saveResourceIfNotExists(String resourcePath) {
        File file = new File(getDataFolder(), resourcePath);
        if (!file.exists()) {
            saveResource(resourcePath, false);
        }
    }

    private void ensurePlayerDataFile(Player player) {
        File dataFolder = new File(getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File playerDataFile = new File(dataFolder, player.getUniqueId() + ".json");
        if (!playerDataFile.exists()) {
            try (FileWriter writer = new FileWriter(playerDataFile)) {
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("waitTime", getConfig().getInt("teleport-wait-time", 5));
                writer.write(jsonObject.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void loadLanguageFile() {
        String language = getConfig().getString("language", "zh_CN");
        File langFile = new File(getDataFolder(), "lang/" + language + ".yml");

        if (!langFile.exists()) {
            saveResource("lang/" + language + ".yml", false);
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);
    }

    private void reloadLanguageFile() {
        String language = getConfig().getString("language", "zh_CN");
        File langFile = new File(getDataFolder(), "lang/" + language + ".yml");

        if (langFile.exists()) {
            langConfig = YamlConfiguration.loadConfiguration(langFile);
            getLogger().info("Language file reloaded.");
        } else {
            getLogger().warning("Language file not found. Reload failed.");
        }
    }

    private String getMessage(String key, String... replacements) {
        String message = langConfig.getString(key, key);
        for (int i = 0; i < replacements.length; i += 2) {
            message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return applyRGBColors(message);
    }

    private String applyRGBColors(String message) {
        Pattern pattern = Pattern.compile("&#[a-fA-F0-9]{6}");
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String color = message.substring(matcher.start() + 1, matcher.end());
            message = message.replace("&" + color, ChatColor.of(color).toString());
            matcher = pattern.matcher(message);
        }
        return message;
    }

    private String applyGradient(String message, Color startColor, Color endColor) {
        String[] parts = message.split(" ");
        int length = parts.length;
        for (int i = 0; i < length; i++) {
            double ratio = (double) i / (length - 1);
            int red = (int) (startColor.getRed() * (1 - ratio) + endColor.getRed() * ratio);
            int green = (int) (startColor.getGreen() * (1 - ratio) + endColor.getGreen() * ratio);
            int blue = (int) (startColor.getBlue() * (1 - ratio) + endColor.getBlue() * ratio);
            String hexColor = String.format("#%02x%02x%02x", red, green, blue);
            parts[i] = ChatColor.of(hexColor) + parts[i];
        }
        return String.join(" ", parts);
    }

    private void setupMetrics() {
        int pluginId = 22684;
        Metrics metrics = new Metrics(this, pluginId);
    }

    private void checkForUpdates(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                URL url = new URL("https://api.tcbmc.cc/update/AutoTeleport/update.json");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String latestVersion = json.get("version").getAsString();

                if (!this.getDescription().getVersion().equalsIgnoreCase(latestVersion)) {
                    sender.sendMessage(getMessage("update_available", "version", latestVersion));
                } else {
                    sender.sendMessage(getMessage("update_not_needed"));
                }
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Failed to check for updates: " + e.getMessage());
            }
        });
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("tpa")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (args.length != 1) {
                    player.sendMessage(getMessage("tpa_usage"));
                    return true;
                }

                Player target = Bukkit.getPlayer(args[0]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage(getMessage("tpa_not_found"));
                    return true;
                }

                if (isOnCooldown(player)) {
                    long remainingTime = getRemainingCooldownTime(player);
                    player.sendMessage(getMessage("cooldown_message", "seconds", String.valueOf(remainingTime)));
                    return true;
                }

                if (!chargePlayer(player, target)) {
                    return true;
                }

                tpaRequests.put(target, player);
                int waitTime = getWaitTime(player);
                boolean bossBarEnabled = getConfig().getBoolean("bossbar-enabled", true);

                target.sendMessage(getMessage("tpa_request_auto", "player", player.getName(), "seconds", String.valueOf(waitTime)));
                target.sendMessage(getMessage("tpa_request"));
                player.sendMessage(getMessage("tpa_sent", "player", target.getName()));

                if (bossBarEnabled) {
                    String bossBarTitle = getMessage("bossbar_title", "player", player.getName(), "seconds", String.valueOf(waitTime));
                    bossBarTitle = applyGradient(bossBarTitle, Color.decode(getConfig().getString("gradient-start-color")), Color.decode(getConfig().getString("gradient-end-color")));
                    bossBar = Bukkit.createBossBar(bossBarTitle, BarColor.BLUE, BarStyle.SOLID);
                    bossBar.addPlayer(target);
                    bossBar.setProgress(1.0);
                }

                BukkitRunnable autoTeleportTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (bossBarEnabled) {
                            bossBar.removePlayer(target);
                            bossBar = null;
                        }
                        if (tpaRequests.get(target) == player) {
                            player.teleport(target);
                            tpaRequests.remove(target);
                            player.sendMessage(getMessage("tpa_teleporting"));
                            target.sendMessage(getMessage("tpa_teleporting"));
                            setCooldown(player);
                        }
                    }
                };
                autoTeleportTasks.put(target, autoTeleportTask);
                autoTeleportTask.runTaskLater(this, waitTime * 20L);

                if (bossBarEnabled) {
                    for (int i = 1; i <= waitTime; i++) {
                        final int countdown = waitTime - i;
                        Bukkit.getScheduler().runTaskLater(this, () -> {
                            if (bossBar != null) {
                                String bossBarTitle = getMessage("bossbar_title", "player", player.getName(), "seconds", String.valueOf(countdown));
                                bossBarTitle = applyGradient(bossBarTitle, Color.decode(getConfig().getString("gradient-start-color")), Color.decode(getConfig().getString("gradient-end-color")));
                                bossBar.setTitle(bossBarTitle);
                                bossBar.setProgress(countdown / (double) waitTime);
                            }
                        }, i * 20L);
                    }
                }
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("tpaccept")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                Player requester = tpaRequests.get(player);
                if (requester != null) {
                    if (bossBar != null) {
                        bossBar.removePlayer(player);
                        bossBar = null;
                    }
                    if (autoTeleportTasks.containsKey(player)) {
                        autoTeleportTasks.get(player).cancel();
                        autoTeleportTasks.remove(player);
                    }
                    requester.teleport(player);
                    tpaRequests.remove(player);
                    player.sendMessage(getMessage("tpa_accepted"));
                    requester.sendMessage(getMessage("tpa_teleporting"));
                } else {
                    player.sendMessage(getMessage("tpa_no_request"));
                }
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("tpdeny")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                Player requester = tpaRequests.remove(player);
                if (requester != null) {
                    if (bossBar != null) {
                        bossBar.removePlayer(player);
                        bossBar = null;
                    }
                    if (autoTeleportTasks.containsKey(player)) {
                        autoTeleportTasks.get(player).cancel();
                        autoTeleportTasks.remove(player);
                    }
                    player.sendMessage(getMessage("tpa_denied"));
                    requester.sendMessage(getMessage("tpa_denied"));
                } else {
                    player.sendMessage(getMessage("tpa_no_request"));
                }
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("tpcancel")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (autoTeleportTasks.containsKey(player)) {
                    autoTeleportTasks.get(player).cancel();
                    autoTeleportTasks.remove(player);
                    player.sendMessage(getMessage("tp_cancelled"));
                    if (bossBar != null) {
                        bossBar.removePlayer(player);
                        bossBar = null;
                    }
                } else {
                    player.sendMessage(getMessage("tpa_no_request"));
                }
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("setwaittime")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (args.length != 1) {
                    player.sendMessage(getMessage("tpa_usage"));
                    return true;
                }
                try {
                    int waitTime = Integer.parseInt(args[0]);
                    setPlayerWaitTime(player, waitTime);
                    player.sendMessage(getMessage("set_wait_time", "seconds", String.valueOf(waitTime)));
                } catch (NumberFormatException e) {
                    player.sendMessage(getMessage("tpa_usage"));
                }
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("tpahelp")) {
            sender.sendMessage(getMessage("tpa_help"));
            return true;
        } else if (command.getName().equalsIgnoreCase("tpareload")) {
            if (sender.hasPermission("autoteleport.reload") || sender.isOp()) {
                reloadConfig();
                reloadLanguageFile();
                sender.sendMessage(getMessage("config_reloaded"));
                checkForUpdates(sender); // 添加此行
            } else {
                sender.sendMessage(getMessage("no_permission"));
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("rtp")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (!isWorldAllowed(player.getWorld().getName())) {
                    player.sendMessage(getMessage("invalid_world"));
                    return true;
                }

                if (isOnCooldown(player)) {
                    long remainingTime = getRemainingCooldownTime(player);
                    player.sendMessage(getMessage("cooldown_message", "seconds", String.valueOf(remainingTime)));
                    return true;
                }

                if (!chargeRandomTeleport(player)) {
                    return true;
                }

                player.sendMessage(getMessage("rtp_searching"));
                Location randomLocation = getRandomSafeLocation(player.getWorld().getSpawnLocation(), getConfig().getInt("random-teleport-range-min", 100), getConfig().getInt("random-teleport-range-max", 1000));

                if (randomLocation != null) {
                    int waitTime = 5; // 设置随机传送等待时间为5秒
                    player.sendMessage(getMessage("rtp_countdown", "seconds", String.valueOf(waitTime)));

                    boolean bossBarEnabled = getConfig().getBoolean("random-teleport-bossbar-enabled", true);
                    if (bossBarEnabled) {
                        String bossBarTitle = getMessage("rtp_countdown", "seconds", String.valueOf(waitTime));
                        bossBarTitle = applyGradient(bossBarTitle, Color.decode(getConfig().getString("gradient-start-color")), Color.decode(getConfig().getString("gradient-end-color")));
                        bossBar = Bukkit.createBossBar(bossBarTitle, BarColor.BLUE, BarStyle.SOLID);
                        bossBar.addPlayer(player);
                        bossBar.setProgress(1.0);

                        for (int i = 1; i <= waitTime; i++) {
                            final int countdown = waitTime - i;
                            Bukkit.getScheduler().runTaskLater(this, () -> {
                                if (bossBar != null) {
                                    String updatedBossBarTitle = getMessage("rtp_countdown", "seconds", String.valueOf(countdown));
                                    updatedBossBarTitle = applyGradient(updatedBossBarTitle, Color.decode(getConfig().getString("gradient-start-color")), Color.decode(getConfig().getString("gradient-end-color")));
                                    bossBar.setTitle(updatedBossBarTitle);
                                    bossBar.setProgress(countdown / (double) waitTime);
                                }
                            }, i * 20L);
                        }
                    }

                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (bossBarEnabled) {
                            bossBar.removePlayer(player);
                            bossBar = null;
                        }
                        player.teleport(randomLocation);
                        player.sendMessage(getMessage("rtp_teleporting"));
                        setCooldown(player);
                        spawnFireworks(randomLocation);
                    }, waitTime * 20L);
                } else {
                    player.sendMessage(getMessage("rtp_not_safe"));
                }
            }
            return true;
        }
        return false;
    }

    private Location getRandomSafeLocation(Location origin, int minRange, int maxRange) {
        for (int i = 0; i < 10; i++) { // Try up to 10 times to find a safe location
            double x = origin.getX() + (random.nextDouble() * (maxRange - minRange) + minRange) * (random.nextBoolean() ? 1 : -1);
            double z = origin.getZ() + (random.nextDouble() * (maxRange - minRange) + minRange) * (random.nextBoolean() ? 1 : -1);
            int y = origin.getWorld().getHighestBlockYAt((int) x, (int) z);
            Location randomLocation = new Location(origin.getWorld(), x, y, z);

            if (isSafeLocation(randomLocation)) {
                return randomLocation;
            }
        }
        return null;
    }

    private boolean isSafeLocation(Location location) {
        Material blockType = location.getBlock().getType();
        Material belowBlockType = location.clone().subtract(0, 1, 0).getBlock().getType();
        Material aboveBlockType = location.clone().add(0, 1, 0).getBlock().getType();
        return blockType == Material.AIR && aboveBlockType == Material.AIR && belowBlockType.isSolid();
    }

    private boolean isWorldAllowed(String worldName) {
        return getConfig().getStringList("allowed-worlds").contains(worldName);
    }

    private boolean isOnCooldown(Player player) {
        Long cooldownEnd = teleportCooldowns.get(player.getUniqueId());
        return cooldownEnd != null && cooldownEnd > System.currentTimeMillis();
    }

    private long getRemainingCooldownTime(Player player) {
        Long cooldownEnd = teleportCooldowns.get(player.getUniqueId());
        if (cooldownEnd == null) return 0;
        return (cooldownEnd - System.currentTimeMillis()) / 1000;
    }

    private void setCooldown(Player player) {
        long cooldownTime = getCooldownTime(player) * 1000L;
        teleportCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + cooldownTime);
    }

    private long getCooldownTime(Player player) {
        for (String key : getConfig().getConfigurationSection("cooldowns").getKeys(false)) {
            if (player.hasPermission(key)) {
                return getConfig().getLong("cooldowns." + key);
            }
        }
        return getConfig().getLong("cooldowns.default");
    }

    private boolean chargePlayer(Player player, Player target) {
        double baseAmount = 0;
        for (String key : getConfig().getConfigurationSection("costs").getKeys(false)) {
            if (player.hasPermission(key)) {
                baseAmount = getConfig().getDouble("costs." + key + ".amount");
                break;
            }
        }

        if (getConfig().getBoolean("charge-by-distance", true)) {
            double distance = player.getLocation().distance(target.getLocation());
            double multiplier = getDistanceCostMultiplier(player);
            baseAmount += distance * multiplier;
            player.sendMessage(getMessage("distance_message", "distance", String.format("%.2f", distance)));
        }

        if (economy != null && !economy.has(player, baseAmount)) {
            player.sendMessage(getMessage("not_enough_money"));
            return false;
        }
        economy.withdrawPlayer(player, baseAmount);
        player.sendMessage(getMessage("cost_message", "amount", String.format("%.2f", baseAmount)));
        return true;
    }

    private double getDistanceCostMultiplier(Player player) {
        for (String key : getConfig().getConfigurationSection("distance-cost-multipliers").getKeys(false)) {
            if (player.hasPermission(key)) {
                return getConfig().getDouble("distance-cost-multipliers." + key);
            }
        }
        return getConfig().getDouble("distance-cost-multipliers.default");
    }

    private boolean chargeRandomTeleport(Player player) {
        for (String key : getConfig().getConfigurationSection("random-teleport-costs").getKeys(false)) {
            if (player.hasPermission(key)) {
                double amount = getConfig().getDouble("random-teleport-costs." + key + ".amount");

                if (economy != null && !economy.has(player, amount)) {
                    player.sendMessage(getMessage("not_enough_money"));
                    return false;
                }
                economy.withdrawPlayer(player, amount);
                player.sendMessage(getMessage("cost_message", "amount", String.format("%.2f", amount)));
                break;
            }
        }
        return true;
    }

    private int getWaitTime(Player player) {
        File playerDataFile = new File(getDataFolder(), "data/" + player.getUniqueId() + ".json");
        if (playerDataFile.exists()) {
            try (FileReader reader = new FileReader(playerDataFile)) {
                JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
                return jsonObject.has("waitTime") ? jsonObject.get("waitTime").getAsInt() : getConfig().getInt("teleport-wait-time", 5);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return getConfig().getInt("teleport-wait-time", 5);
    }

    private void setPlayerWaitTime(Player player, int waitTime) {
        File playerDataFile = new File(getDataFolder(), "data/" + player.getUniqueId() + ".json");
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("waitTime", waitTime);
        try (FileWriter writer = new FileWriter(playerDataFile)) {
            writer.write(jsonObject.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void spawnFireworks(Location location) {
        Bukkit.getScheduler().runTask(this, () -> {
            for (int i = 0; i < 3; i++) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    location.getWorld().spawn(location, org.bukkit.entity.Firework.class, (firework) -> {
                        firework.setVelocity(new Vector(0, 1, 0));
                        firework.detonate();
                    });
                }, i * 10L);
            }
        });
    }
}
