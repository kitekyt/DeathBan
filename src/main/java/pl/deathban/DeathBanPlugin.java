package pl.deathban;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Date;
import java.util.List;

public class DeathBanPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private static final String BAN_MESSAGE = "You are banned permanently on this server!";
    private static final int SLOT_SOUND = 11;
    private static final int SLOT_BAN = 15;

    private boolean deathBanEnabled;
    private boolean deathSoundEnabled;

    /** Holder służy do rozpoznania naszego menu. */
    private static class MenuHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        deathBanEnabled = getConfig().getBoolean("death-ban-enabled", true);
        deathSoundEnabled = getConfig().getBoolean("death-sound-enabled", true);

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("deathban").setExecutor(this);
        getLogger().info("DeathBan enabled.");
    }

    // ---------------------------------------------------------------- komenda

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy.");
            return true;
        }
        if (!player.hasPermission("deathban.admin")) {
            player.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }
        openMenu(player);
        return true;
    }

    // ------------------------------------------------------------------- menu

    private void openMenu(Player player) {
        MenuHolder holder = new MenuHolder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("DeathBan Settings", NamedTextColor.DARK_RED));
        holder.inventory = inv;
        fillMenu(inv);
        player.openInventory(inv);
    }

    private void fillMenu(Inventory inv) {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.text(" "));
        filler.setItemMeta(fm);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        inv.setItem(SLOT_SOUND, buildItem(Material.JUKEBOX, "Death Sound",
                deathSoundEnabled, "Wither spawn sound when a player dies."));
        inv.setItem(SLOT_BAN, buildItem(Material.SKELETON_SKULL, "Death Ban",
                deathBanEnabled, "Permanent ban when a player dies."));
    }

    private ItemStack buildItem(Material material, String title, boolean enabled, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(title, NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(description, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Status: ", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(enabled
                                ? Component.text("ON", NamedTextColor.GREEN, TextDecoration.BOLD)
                                : Component.text("OFF", NamedTextColor.RED, TextDecoration.BOLD)),
                Component.empty(),
                Component.text("Click to turn " + (enabled ? "OFF" : "ON"), NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) {
            return;
        }
        event.setCancelled(true);

        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.hasPermission("deathban.admin")) {
            return;
        }

        int slot = event.getSlot();
        if (slot == SLOT_SOUND) {
            deathSoundEnabled = !deathSoundEnabled;
            getConfig().set("death-sound-enabled", deathSoundEnabled);
        } else if (slot == SLOT_BAN) {
            deathBanEnabled = !deathBanEnabled;
            getConfig().set("death-ban-enabled", deathBanEnabled);
        } else {
            return;
        }

        saveConfig();
        fillMenu(event.getView().getTopInventory());
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------- śmierć

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();

        if (dead.hasPermission("deathban.bypass")) {
            return;
        }

        if (deathSoundEnabled) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.playSound(online.getLocation(), Sound.ENTITY_WITHER_SPAWN,
                        SoundCategory.MASTER, 1.0f, 1.0f);
            }
        }

        if (deathBanEnabled) {
            Bukkit.getBanList(BanList.Type.NAME)
                    .addBan(dead.getName(), "§c" + BAN_MESSAGE, (Date) null, "DeathBan");

            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (dead.isOnline()) {
                    dead.kick(Component.text(BAN_MESSAGE, NamedTextColor.RED));
                }
            }, 40L);
        }
    }
}
