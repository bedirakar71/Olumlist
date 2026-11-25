package com.zornoely.olumlist;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OlumListPlugin extends JavaPlugin implements Listener {

    private static final String TITLE_PREFIX = "&5&lÖlüm &8| &dS.";
    private static final String PLAIN_PREFIX = "Ölüm | S.";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        createDataFile();

        if (dataConfig == null) {
            getLogger().warning("deaths.yml yuklenemedi! Eklenti devre disi birakiliyor.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("olumlist") != null) {
            getCommand("olumlist").setExecutor(this);
        } else {
            getLogger().warning("'olumlist' komutu plugin.yml icinde bulunamadi!");
        }

        getLogger().info("OlumList (1.21.4) aktif edildi!");
    }

    private void createDataFile() {
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                getLogger().warning("Eklenti klasoru olusturulamadi: " + getDataFolder().getAbsolutePath());
            }

            dataFile = new File(getDataFolder(), "deaths.yml");
            if (!dataFile.exists() && !dataFile.createNewFile()) {
                getLogger().warning("deaths.yml dosyasi olusturulamadi!");
                return;
            }

            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        } catch (IOException e) {
            getLogger().warning("deaths.yml olusturulurken hata olustu: " + e.getMessage());
        }
    }

    public void addDeathRecord(UUID uuid, String record) {
        if (uuid == null || dataConfig == null) return;

        String path = "players." + uuid;
        List<String> list = dataConfig.getStringList(path);
        list.add(0, record);
        dataConfig.set(path, list);
        saveData();
    }

    public List<String> getDeathRecords(UUID uuid) {
        if (uuid == null || dataConfig == null) return new ArrayList<>();
        return new ArrayList<>(dataConfig.getStringList("players." + uuid));
    }

    public void saveData() {
        if (dataConfig == null || dataFile == null) return;
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("deaths.yml kaydedilirken hata olustu: " + e.getMessage());
        }
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player == null) return;

        String dateTime = LocalDateTime.now(ZoneId.of("Europe/Istanbul")).format(FORMATTER);
        String worldName = player.getWorld().getName();
        String killer = (player.getKiller() != null)
                ? player.getKiller().getName()
                : "Çevre / Yaratık";

        String record = dateTime + "|" + worldName + "|" + killer;
        addDeathRecord(player.getUniqueId(), record);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cBu komutu sadece oyuncular kullanabilir."));
            return true;
        }

        if (!player.hasPermission("olumlist.use")) {
            player.sendMessage(color("&cBu komutu kullanma yetkin yok."));
            return true;
        }

        openMenu(player, 1);
        return true;
    }

    public void openMenu(Player player, int page) {
        if (player == null) return;

        List<String> records = getDeathRecords(player.getUniqueId());
        int itemsPerPage = 45;
        int totalPages = Math.max(1, (int) Math.ceil((double) records.size() / itemsPerPage));

        int safePage = Math.max(1, Math.min(page, totalPages));
        String title = color(TITLE_PREFIX + safePage);

        Inventory inv = Bukkit.createInventory(null, 54, title);

        int startIndex = (safePage - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, records.size());

        for (int i = startIndex; i < endIndex; i++) {
            int deathNumber = records.size() - i;
            String[] parts = records.get(i).split("\\|");

            String dateTime = parts.length > 0 ? parts[0] : "Bilinmiyor";
            String world = parts.length > 1 ? parts[1] : "Bilinmiyor";
            String killer = parts.length > 2 ? parts[2] : "Bilinmiyor";

            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta meta = paper.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color("&d&lÖlüm #" + deathNumber));
                List<String> lore = new ArrayList<>();
                lore.add(color("&8&m-----------------------"));
                lore.add(color("&7Tarih & Saat: &f" + dateTime));
                lore.add(color("&7Öldüren: &d" + killer));
                lore.add(color("&7Dünya: &5" + world));
                lore.add(color("&8&m-----------------------"));
                meta.setLore(lore);
                paper.setItemMeta(meta);
            }
            inv.setItem(i - startIndex, paper);
        }

        ItemStack glass = createGuiItem(Material.MAGENTA_STAINED_GLASS_PANE, " ");
        for (int slot = 45; slot < 54; slot++) {
            inv.setItem(slot, glass);
        }

        if (safePage > 1) {
            inv.setItem(45, createGuiItem(Material.ARROW, "&d« Önceki Sayfa", "&7Sayfa " + (safePage - 1)));
        }

        inv.setItem(49, createGuiItem(Material.BOOK, "&5&lToplam Ölüm: &d" + records.size(),
                "&7Sayfa: &f" + safePage + " / " + totalPages));

        if (safePage < totalPages) {
            inv.setItem(53, createGuiItem(Material.ARROW, "&dSonraki Sayfa »", "&7Sayfa " + (safePage + 1)));
        }

        player.openInventory(inv);
    }

    private ItemStack createGuiItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (loreLines.length > 0) {
                List<String> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(color(line));
                }
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isDeathMenu(String rawTitle) {
        if (rawTitle == null) return false;
        String coloredPrefix = color(TITLE_PREFIX);
        String strippedTitle = ChatColor.stripColor(rawTitle);

        return rawTitle.startsWith(coloredPrefix)
                || (strippedTitle != null && strippedTitle.startsWith(PLAIN_PREFIX));
    }

    private int extractPageNumber(String rawTitle) {
        if (rawTitle == null) return 1;
        String stripped = ChatColor.stripColor(rawTitle);
        if (stripped == null || !stripped.startsWith(PLAIN_PREFIX)) return 1;

        try {
            return Integer.parseInt(stripped.substring(PLAIN_PREFIX.length()).trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (!isDeathMenu(title)) return;

        event.setCancelled(true);

        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int currentPage = extractPageNumber(title);

        if (event.getSlot() == 45 && clicked.getType() == Material.ARROW) {
            openMenu(player, currentPage - 1);
        } else if (event.getSlot() == 53 && clicked.getType() == Material.ARROW) {
            openMenu(player, currentPage + 1);
        }
    }
}
