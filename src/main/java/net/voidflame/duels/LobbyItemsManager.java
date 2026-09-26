package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class LobbyItemsManager implements Listener {
    private static final String KIT_MENU = "§8VoidFlame • Choose Kit";
    private static final String PARTY_MENU = "§8VoidFlame • Party";
    private final VoidFlameDuelsPlugin plugin;

    public LobbyItemsManager(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> giveLobbyItems(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Nothing persistent is held here; the items are rebuilt on the next join.
    }

    public void giveLobbyItems(Player player) {
        if (plugin.matchManager().isInMatch(player.getUniqueId())
                || plugin.matchManager().isDisconnected(player.getUniqueId())
                || plugin.spectatorManager().isSpectating(player.getUniqueId())) return;

        PlayerInventoryAccess.clearAndPlace(player);
        player.updateInventory();
    }

    public void openKitMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, KIT_MENU);
        fill(inv);
        int slot = 10;
        for (KitType kit : KitType.values()) {
            if (slot >= 35) break;
            Material icon = switch (kit) {
                case SWORD -> Material.IRON_SWORD;
                case AXE -> Material.NETHERITE_AXE;
                case UHC -> Material.GOLDEN_APPLE;
                case MACE -> Material.MACE;
                case SPEAR_MACE -> Material.TRIDENT;
                case CRYSTAL -> Material.END_CRYSTAL;
                case NETHERITE_OP -> Material.NETHERITE_CHESTPLATE;
            };
            button(inv, slot++, icon, "§b" + pretty(kit), "§7اضغط لاختيار هذا الكيت");
        }
        button(inv, 31, Material.ARROW, "§7إغلاق");
        player.openInventory(inv);
    }

    public void openPartyMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, PARTY_MENU);
        fill(inv);
        button(inv, 10, Material.DIAMOND_SWORD, "§bParty 1v1", "§7مباراة لاعب ضد لاعب من البارتي");
        button(inv, 13, Material.IRON_SWORD, "§aParty 2v2", "§7مباراة فريقين، لاعبان ضد لاعبين");
        button(inv, 16, Material.TNT, "§cParty FFA", "§7كل أعضاء البارتي ضد بعضهم");
        button(inv, 22, Material.PLAYER_HEAD, "§eParty Members", "§7عرض أعضاء البارتي");
        button(inv, 31, Material.ARROW, "§7إغلاق");
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.equals(KIT_MENU) && !title.equals(PARTY_MENU)) return;
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) return;

        if (title.equals(KIT_MENU)) {
            int slot = event.getRawSlot();
            int index = slot - 10;
            if (index >= 0 && index < KitType.values().length && slot < 29) {
                KitType kit = KitType.values()[index];
                if (plugin.kitManager().apply(player, kit)) {
                    player.sendMessage(plugin.message("kit-selected").replace("<kit>", pretty(kit)));
                    player.closeInventory();
                    giveLobbyItems(player);
                } else {
                    player.sendMessage(plugin.message("unknown-kit"));
                }
            } else if (slot == 31) {
                player.closeInventory();
            }
            return;
        }

        switch (event.getRawSlot()) {
            case 10 -> startPartyMode(player, PartyMode.ONE_V_ONE);
            case 13 -> startPartyMode(player, PartyMode.TWO_V_TWO);
            case 16 -> startPartyMode(player, PartyMode.FFA);
            case 22 -> {
                PartyManager.Party party = plugin.partyManager().partyOf(player.getUniqueId());
                if (party == null) {
                    player.sendMessage(plugin.message("party-not-in"));
                } else {
                    player.sendMessage(ChatColor.AQUA + "Party members: " + party.members().stream()
                            .map(id -> {
                                Player p = Bukkit.getPlayer(id);
                                return p == null ? Bukkit.getOfflinePlayer(id).getName() : p.getName();
                            })
                            .filter(java.util.Objects::nonNull)
                            .reduce((a,b) -> a + ", " + b).orElse("-"));
                }
            }
            case 31 -> player.closeInventory();
            default -> {}
        }
    }

    private void startPartyMode(Player player, PartyMode mode) {
        if (plugin.partyManager().partyOf(player.getUniqueId()) == null) {
            player.sendMessage(plugin.message("party-not-in"));
            return;
        }
        if (!plugin.partyManager().isLeader(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "فقط قائد البارتي يقدر يبدأ هذا الطور.");
            return;
        }
        plugin.partyManager().setMode(player.getUniqueId(), mode);
        player.sendMessage(ChatColor.GREEN + "Party mode: " + mode.displayName + " §7تم اختياره.");
        player.closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (title.equals(KIT_MENU) || title.equals(PARTY_MENU)) event.setCancelled(true);
    }

    private void fill(Inventory inv) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler.clone());
    }

    private void button(Inventory inv, int slot, Material material, String name, String... lore) {
        inv.setItem(slot, item(material, name, lore));
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String pretty(KitType kit) {
        return kit == KitType.SPEAR_MACE ? "Spear & Mace" : kit.name().replace('_', ' ');
    }

    public enum PartyMode {
        ONE_V_ONE("Party 1v1"),
        TWO_V_TWO("Party 2v2"),
        FFA("Party FFA");

        private final String displayName;
        PartyMode(String displayName) { this.displayName = displayName; }
    }

    private static final class PlayerInventoryAccess {
        static void clearAndPlace(Player player) {
            var inv = player.getInventory();
            inv.clear();
            inv.setItem(0, named(Material.IRON_SWORD, "§bKit Selector", "§7Right-click to choose your kit"));
            inv.setItem(1, named(Material.GOAT_HORN, "§dParty +", "§7Right-click to open party"));
            inv.setItem(8, named(Material.BOOK, "§6Kit Editor", "§7Right-click to edit your kit"));
        }

        private static ItemStack named(Material material, String name, String lore) {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(name);
                meta.setLore(List.of(lore));
                item.setItemMeta(meta);
            }
            return item;
        }
    }
}
