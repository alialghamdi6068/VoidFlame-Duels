package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import net.voidflame.core.storage.StorageService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LobbyItemsManager implements Listener {
    private final VoidFlameDuelsPlugin plugin;
    private final NamespacedKey key;
    private final Map<UUID, UUID> targetByViewer = new ConcurrentHashMap<>();

    public LobbyItemsManager(VoidFlameDuelsPlugin plugin) {
        this.plugin=plugin;
        this.key=new NamespacedKey(plugin,"spawn-item");
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTask(plugin,()->giveLobbyItems(e.getPlayer()));
    }

    public void giveLobbyItems(Player p) {
        if(plugin.matchManager().isInMatch(p.getUniqueId()) || plugin.matchManager().isDisconnected(p.getUniqueId())
                || plugin.matchManager().hasPendingRestore(p.getUniqueId())
                || plugin.spectatorManager().isSpectating(p.getUniqueId())
                || plugin.ffaManager().isInFfa(p.getUniqueId())) return;
        PlayerInventory inv=p.getInventory();
        inv.setItem(0, item(Material.DIAMOND_SWORD,"§5§lDUEL","§7Right-click §8» §fChoose a kit and play","§7Left-click a player §8» §fChoose a kit and duel"));
        inv.setItem(1, item(Material.GOAT_HORN,"§b§lPARTY +","§7Right-click §8» §fOpen Party"));
        for(int i=2;i<=7;i++) inv.setItem(i,null);
        inv.setItem(7, item(Material.BOOK,"§d§lKIT EDITOR","§7Right-click §8» §fChoose a kit layout to edit"));
        inv.setItem(8, item(Material.NETHER_STAR,"§6§lLEADERBOARD","§7Right-click §8» §fView top players"));
        mark(inv.getItem(0),"duel"); mark(inv.getItem(1),"party"); mark(inv.getItem(7),"editor"); mark(inv.getItem(8),"leaderboard");
        p.updateInventory();
    }

    public void enforceDuelItems() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (plugin.matchManager().isInMatch(id) || plugin.matchManager().isDisconnected(id)
                    || plugin.matchManager().hasPendingRestore(id) || plugin.spectatorManager().isSpectating(id)
                    || plugin.ffaManager().isInFfa(id) || plugin.kitEditorManager().isEditing(id)
                    || plugin.partyManager().partyOf(id) != null) continue;

            PlayerInventory inventory = player.getInventory();
            ItemStack current = inventory.getItem(0);
            if (isType(current, "duel")) continue;

            int existingDuelSlot = -1;
            ItemStack[] contents = inventory.getStorageContents();
            for (int slot = 0; slot < contents.length; slot++) {
                if (slot != 0 && isType(contents[slot], "duel")) {
                    existingDuelSlot = slot;
                    break;
                }
            }

            if (current != null && current.getType() != Material.AIR) {
                int emptySlot = -1;
                for (int slot = 9; slot < Math.min(36, contents.length); slot++) {
                    if (contents[slot] == null || contents[slot].getType() == Material.AIR) {
                        emptySlot = slot;
                        break;
                    }
                }
                if (emptySlot < 0) continue; // Never delete the item occupying the reserved slot.
                inventory.setItem(emptySlot, current.clone());
            }

            ItemStack duelItem;
            if (existingDuelSlot >= 0) {
                duelItem = inventory.getItem(existingDuelSlot);
                inventory.setItem(existingDuelSlot, null);
            } else {
                duelItem = item(Material.DIAMOND_SWORD, "§5§lDUEL",
                        "§7Right-click §8» §fChoose a kit and play",
                        "§7Left-click a player §8» §fChoose a kit and duel");
                mark(duelItem, "duel");
            }
            inventory.setItem(0, duelItem);
        }
    }

    private boolean isReservedLobbySlot(int slot) {
        return slot == 0 || slot == 1 || slot == 7 || slot == 8;
    }

    public void givePartyItems(Player p) {
        if (plugin.matchManager().isInMatch(p.getUniqueId())
                || plugin.matchManager().isDisconnected(p.getUniqueId())
                || plugin.spectatorManager().isSpectating(p.getUniqueId())) return;
        PlayerInventory inv = p.getInventory();
        inv.setItem(0, item(Material.PLAYER_HEAD, "§b§lParty", "§7Right-click §8» §fManage your party"));
        inv.setItem(1, item(Material.NETHER_STAR, "§d§lParty Queue", "§7Right-click §8» §fChoose a party mode"));
        inv.setItem(2, item(Material.NAME_TAG, "§e§lInvite", "§7Right-click §8» §fInvite a player"));
        inv.setItem(7, item(Material.BOOK, "§a§lParty Info", "§7Right-click §8» §fView members"));
        inv.setItem(8, item(Material.BARRIER, "§c§lLeave Party", "§7Right-click §8» §fLeave / disband"));
        for (int i = 3; i <= 6; i++) inv.setItem(i, null);
        mark(inv.getItem(0), "party-menu");
        mark(inv.getItem(1), "party-queue");
        mark(inv.getItem(2), "party-invite");
        mark(inv.getItem(7), "party-info");
        mark(inv.getItem(8), "party-leave");
        p.updateInventory();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        // The spawn sword is a permanent menu item, never a death drop.
        event.getDrops().removeIf(this::is);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) giveLobbyItems(player);
        });
    }

    @EventHandler public void onInteract(PlayerInteractEvent e) {
        if(e.getHand()!=EquipmentSlot.HAND)return;
        if(e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
        ItemStack it=e.getItem(); if(!is(it))return;
        e.setCancelled(true);
        String type=it.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING);
        Player p=e.getPlayer();
        if("duel".equals(type)) openSelfKit(p);
        else if("party".equals(type) || "party-menu".equals(type)) openParty(p);
        else if("party-queue".equals(type)) openParty(p);
        else if("party-invite".equals(type)) p.sendMessage("§bParty §7» §fUse §e/party invite <player> §fto invite someone.");
        else if("party-info".equals(type)) showMembers(p);
        else if("party-leave".equals(type)) leaveParty(p);
        else if("editor".equals(type)) openKitEditorMenu(p);
        else if("leaderboard".equals(type)) openLeaderboard(p);
    }

    @EventHandler public void onInteractEntity(PlayerInteractEntityEvent e) {
        if(e.getHand()!=EquipmentSlot.HAND || !(e.getRightClicked() instanceof Player target)) return;
        Player p=e.getPlayer();
        if(p.equals(target) || plugin.matchManager().isInMatch(p.getUniqueId()) || plugin.matchManager().isInMatch(target.getUniqueId())) return;
        ItemStack held=p.getInventory().getItemInMainHand();
        if(!isType(held,"duel")) return;
        e.setCancelled(true);
        openTargetKit(p,target);
    }

    @EventHandler public void onLeftClickPlayer(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof Player target)) return;
        if (p.equals(target)) return;
        if (plugin.matchManager().isInMatch(p.getUniqueId()) || plugin.matchManager().isInMatch(target.getUniqueId())) return;
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!isType(held, "duel")) return;
        e.setCancelled(true);
        openTargetKit(p, target);
    }

    private void openKitEditorMenu(Player player) {
        plugin.nativeMenus().openEditorChoice(player);
    }

    private String kitColor(KitType kit) {
        return switch (kit) {
            case TNT_MINECART_LT -> "§6";
            case TNT_MINECART_HT -> "§c";
            case DIAMOND_SMP -> "§b";
            default -> "§d";
        };
    }

    private Material iconFor(KitType kit) {
        return switch (kit) {
            case SWORD -> Material.DIAMOND_SWORD;
            case AXE -> Material.DIAMOND_AXE;
            case UHC -> Material.GOLDEN_APPLE;
            case MACE -> Material.MACE;
            case SPEAR_MACE -> Material.TRIDENT;
            case CRYSTAL -> Material.END_CRYSTAL;
            case NETHERITE_POT -> Material.SPLASH_POTION;
            case SMP -> Material.TOTEM_OF_UNDYING;
            case DIAMOND_SMP -> Material.DIAMOND_CHESTPLATE;
            case TNT_MINECART_LT, TNT_MINECART_HT -> Material.TNT_MINECART;
        };
    }

    public void openLeaderboard(Player player) {
        plugin.nativeMenus().openLeaderboard(player);
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private void openSelfKit(Player p){
        plugin.nativeMenus().openQueue(p);
    }

    private void openTargetKit(Player p,Player target){
        plugin.nativeMenus().openTargetKit(p, target);
    }

    private void openParty(Player p){
        plugin.nativeMenus().openParty(p);
    }

    private void showMembers(Player p){
        PartyManager.Party party=plugin.partyManager().partyOf(p.getUniqueId());
        if(party==null){p.sendMessage(plugin.message("party-not-in"));return;}
        p.sendMessage(ChatColor.AQUA+"Party members: "+party.members().stream().map(id->{Player x=Bukkit.getPlayer(id);return x==null?Bukkit.getOfflinePlayer(id).getName():x.getName();}).filter(java.util.Objects::nonNull).reduce((a,b)->a+", "+b).orElse("-"));
    }

    private void leaveParty(Player p){
        PartyManager.Party party=plugin.partyManager().partyOf(p.getUniqueId());
        if(party==null){p.sendMessage(plugin.message("party-not-in"));return;}
        if(party.leader().equals(p.getUniqueId())){
            plugin.partyManager().disband(p);
        }else{
            plugin.partyManager().leave(p);
        }
        giveLobbyItems(p);
        p.closeInventory();
        p.sendMessage(plugin.message("party-left"));
    }

    @EventHandler public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (plugin.matchManager().isInMatch(player.getUniqueId())
                || plugin.matchManager().isDisconnected(player.getUniqueId())
                || plugin.spectatorManager().isSpectating(player.getUniqueId())) return;

        // Protect the lobby items themselves and their fixed hotbar slots.
        if (is(e.getCurrentItem()) || is(e.getCursor())) {
            e.setCancelled(true);
            return;
        }
        if (e.getClickedInventory() instanceof PlayerInventory clickedInventory) {
            int slot = e.getSlot();
            if (slot >= 0 && slot <= 8
                    && (isReservedLobbySlot(slot) || is(clickedInventory.getItem(slot)))) {
                e.setCancelled(true);
                return;
            }
        }

        int hotbarButton = e.getHotbarButton();
        if ((e.getClick() == ClickType.NUMBER_KEY || e.getAction() == InventoryAction.HOTBAR_SWAP)
                && hotbarButton >= 0 && hotbarButton <= 8
                && is(player.getInventory().getItem(hotbarButton))) {
            e.setCancelled(true);
            return;
        }
        if (e.getClick() == ClickType.SWAP_OFFHAND
                && (is(player.getInventory().getItemInOffHand()) || is(player.getInventory().getItemInMainHand()))) {
            e.setCancelled(true);
        }
    }

    private void startParty(Player p,PartyMode mode){
        if(plugin.partyManager().partyOf(p.getUniqueId())==null){plugin.partyManager().create(p);}
        if(!plugin.partyManager().isLeader(p.getUniqueId())){p.sendMessage("§cOnly the party leader can start the match.");return;}
        plugin.partyManager().setMode(p.getUniqueId(),mode);
        if(plugin.getConfig().getBoolean("settings.party-auto-start",true)&&plugin.matchManager().startParty(p.getUniqueId())){p.closeInventory();return;}
        plugin.partyManager().queue(p.getUniqueId(),mode);p.closeInventory();
    }

    @EventHandler public void onDrop(PlayerDropItemEvent e) {
        if (is(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (is(e.getOldCursor())) {
            e.setCancelled(true);
            return;
        }
        for (int rawSlot : e.getRawSlots()) {
            if (e.getView().getInventory(rawSlot) == player.getInventory()) {
                int slot = e.getView().convertSlot(rawSlot);
                if (slot >= 0 && slot <= 8
                        && (isReservedLobbySlot(slot) || is(player.getInventory().getItem(slot)))) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    private boolean is(ItemStack x){if(x==null||x.getType()==Material.AIR||x.getItemMeta()==null)return false;return x.getItemMeta().getPersistentDataContainer().has(key,PersistentDataType.STRING);}
    private boolean isType(ItemStack x,String type){if(!is(x))return false;return type.equals(x.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING));}
    private void mark(ItemStack x,String type){if(x==null||x.getItemMeta()==null)return;ItemMeta m=x.getItemMeta();m.getPersistentDataContainer().set(key,PersistentDataType.STRING,type);x.setItemMeta(m);}
    private ItemStack item(Material m,String name,String... lore){ItemStack x=new ItemStack(m);ItemMeta meta=x.getItemMeta();if(meta!=null){meta.setDisplayName(name);meta.setLore(List.of(lore));x.setItemMeta(meta);}return x;}
    private void fill(Inventory inv){
        ItemStack border=item(Material.BLACK_STAINED_GLASS_PANE," ");
        ItemStack accent=item(Material.PURPLE_STAINED_GLASS_PANE," ");
        for(int slot=0;slot<inv.getSize();slot++){
            int row=slot/9,col=slot%9;
            if(row==0||row==inv.getSize()/9-1||col==0||col==8)inv.setItem(slot,border.clone());
        }
        for(int col:new int[]{1,2,3,5,6,7}){
            inv.setItem(col,accent.clone());
            inv.setItem(inv.getSize()-9+col,accent.clone());
        }
    }
    private String pretty(KitType k){return k==KitType.SPEAR_MACE?"Spear & Mace":k.name().replace('_',' ');}

    public enum PartyMode {
        ONE_V_ONE("Party 1v1"), TWO_V_TWO("Party 2v2"), FFA("Party FFA");
        private final String displayName;
        PartyMode(String displayName){this.displayName=displayName;}
        public String displayName(){return displayName;}
    }
}