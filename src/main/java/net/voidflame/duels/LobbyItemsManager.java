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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LobbyItemsManager implements Listener {
    private static final String DUEL_MENU = "§8VoidFlame §7• §dDuel";
    private static final String PARTY_MENU = "§8VoidFlame §7• §bParty";
    private static final String TARGET_PREFIX = "§8VoidFlame §7• §dDuel ";
    private static final String PARTY_GUI = "§8VoidFlame §7• §bParty";
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
                || plugin.spectatorManager().isSpectating(p.getUniqueId())) return;
        PlayerInventory inv=p.getInventory();
        inv.setItem(0, item(Material.DIAMOND_SWORD,"§d§lDuel","§7Right-click §8» §fChoose a kit and play","§7Left-click a player §8» §fChoose a kit and duel"));
        inv.setItem(1, item(Material.GOAT_HORN,"§b§lParty +","§7Right-click §8» §fOpen Party"));
        for(int i=2;i<=7;i++) inv.setItem(i,null);
        inv.setItem(8, item(Material.BOOK,"§e§lKit Editor","§7Right-click §8» §fOpen Kit Editor"));
        mark(inv.getItem(0),"duel"); mark(inv.getItem(1),"party"); mark(inv.getItem(8),"editor");
        p.updateInventory();
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
        else if("editor".equals(type)) plugin.kitManager().openEditor(p,KitType.SWORD);
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

    private void openSelfKit(Player p){openKitMenu(p,DUEL_MENU,null);}
    private void openTargetKit(Player p,Player target){targetByViewer.put(p.getUniqueId(),target.getUniqueId());openKitMenu(p,TARGET_PREFIX+target.getName(),target.getUniqueId());}

    private void openKitMenu(Player p,String title,UUID target) {
        Inventory inv=Bukkit.createInventory(null,27,title);
        fill(inv);
        int[] slots={10,11,12,13,14,15,16,19};
        KitType[] kits=KitType.values();
        for(int i=0;i<Math.min(slots.length,kits.length);i++) {
            KitType k=kits[i];
            Material icon=switch(k){case SWORD->Material.DIAMOND_SWORD;case AXE->Material.DIAMOND_AXE;case UHC->Material.GOLDEN_APPLE;case MACE->Material.MACE;case SPEAR_MACE->Material.TRIDENT;case CRYSTAL->Material.END_CRYSTAL;};
            inv.setItem(slots[i],item(icon,"§d§l"+pretty(k),"§7Click to "+(target==null?"play":"send a duel request")));
        }
        inv.setItem(18,item(Material.ARROW,"§7§lBack"));
        inv.setItem(26,item(Material.BARRIER,"§c§lClose"));
        p.openInventory(inv);
    }

    private void openParty(Player p){
        if (plugin.partyManager().partyOf(p.getUniqueId()) == null) {
            plugin.partyManager().create(p);
            givePartyItems(p);
        }
        Inventory inv=Bukkit.createInventory(null,54,PARTY_GUI);
        fillModern(inv);
        inv.setItem(4,item(Material.NETHER_STAR,"§b§lYOUR PARTY","§7Manage your party and choose a mode."));
        PartyManager.Party party=plugin.partyManager().partyOf(p.getUniqueId());
        if (party != null) {
            int slot=19;
            for (UUID id : party.members()) {
                Player member=Bukkit.getPlayer(id);
                String name=member==null?Bukkit.getOfflinePlayer(id).getName():""+member.getName();
                ItemStack head=new ItemStack(Material.PLAYER_HEAD);
                ItemMeta meta=head.getItemMeta();
                if(meta!=null){meta.setDisplayName("§f"+name+(id.equals(party.leader())?" §6★":""));meta.setLore(List.of("§7Party member","§7Click for party actions."));head.setItemMeta(meta);}
                inv.setItem(slot,head); slot += slot%9==7 ? 3 : 1; if(slot>=44) break;
            }
        }
        inv.setItem(45,item(Material.DIAMOND_SWORD,"§d§lParty 1v1","§7Queue your party for 1v1."));
        inv.setItem(47,item(Material.IRON_SWORD,"§a§lParty 2v2","§7Queue your party for 2v2."));
        inv.setItem(49,item(Material.TNT,"§c§lParty FFA","§7Queue your party for FFA."));
        inv.setItem(51,item(Material.NAME_TAG,"§e§lInvite","§7Use /party invite <player>."));
        inv.setItem(52,item(Material.ARROW,"§7§lBack","§7Return to your lobby items."));
        inv.setItem(53,item(Material.BARRIER,"§c§lClose","§7Close the menu."));
        p.openInventory(inv);
    }

    private void fillModern(Inventory inv){
        ItemStack border=item(Material.BLACK_STAINED_GLASS_PANE," ");
        ItemStack accent=item(Material.PURPLE_STAINED_GLASS_PANE," ");
        for(int s=0;s<inv.getSize();s++){
            int r=s/9,c=s%9;
            if(r==0||r==5||c==0||c==8) inv.setItem(s,border.clone());
        }
        for(int s: new int[]{1,2,3,5,6,7,10,11,12,14,15,16,46,48,50}) inv.setItem(s,accent.clone());
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

    @EventHandler public void onClick(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        String title=e.getView().getTitle();
        if(title.equals(DUEL_MENU)||title.startsWith(TARGET_PREFIX)||title.equals(PARTY_MENU)||title.equals(PARTY_GUI)){
            e.setCancelled(true);
            int s=e.getRawSlot();
            if(s<0)return;
            if(title.equals(PARTY_GUI)){
                if(s>=54)return;
                switch(s){
                    case 45 -> startParty(p,PartyMode.ONE_V_ONE);
                    case 47 -> startParty(p,PartyMode.TWO_V_TWO);
                    case 49 -> startParty(p,PartyMode.FFA);
                    case 51 -> p.sendMessage("§bParty §7» §fUse §e/party invite <player> §fto invite.");
                    case 52 -> { p.closeInventory(); givePartyItems(p); }
                    case 53 -> p.closeInventory();
                    default -> {}
                }
                return;
            }
            if(s>=27)return;
            if(s==26){targetByViewer.remove(p.getUniqueId());p.closeInventory();return;}
            if(s==18){targetByViewer.remove(p.getUniqueId());p.closeInventory();return;}
            int[] slots={10,11,12,13,14,15,16,19};
            for(int i=0;i<slots.length&&i<KitType.values().length;i++) if(s==slots[i]){
                KitType kit=KitType.values()[i];
                UUID target=targetByViewer.get(p.getUniqueId());
                if(title.startsWith(TARGET_PREFIX) && target!=null){
                    Player t=Bukkit.getPlayer(target);
                    if(t==null){p.sendMessage(plugin.message("player-not-found"));p.closeInventory();return;}
                    if(plugin.requests().send(p,t,kit)) p.sendMessage(plugin.message("duel-sent").replace("<player>",t.getName()));
                    else p.sendMessage(plugin.message("duel-unavailable"));
                } else if(plugin.queueManager().join(p,kit,false)){
                    p.sendMessage(plugin.message("joined-queue").replace("<kit>",pretty(kit)).replace("<type>","Unranked"));
                } else p.sendMessage(plugin.message("already-queued"));
                targetByViewer.remove(p.getUniqueId());p.closeInventory();return;
            }
            if(title.equals(PARTY_MENU)){
                switch(s){
                    case 10->startParty(p,PartyMode.ONE_V_ONE);
                    case 13->startParty(p,PartyMode.TWO_V_TWO);
                    case 16->startParty(p,PartyMode.FFA);
                    case 22->showMembers(p);
                    default->{}
                }
            }
            return;
        }
        if(plugin.matchManager().isInMatch(p.getUniqueId()))return;
        if(e.getRawSlot()<9&&is(e.getCurrentItem()))e.setCancelled(true);
        if(e.isShiftClick()&&is(e.getCurrentItem()))e.setCancelled(true);
    }

    private void startParty(Player p,PartyMode mode){
        if(plugin.partyManager().partyOf(p.getUniqueId())==null){plugin.partyManager().create(p);}
        if(!plugin.partyManager().isLeader(p.getUniqueId())){p.sendMessage("§cOnly the party leader can start the match.");return;}
        plugin.partyManager().setMode(p.getUniqueId(),mode);
        if(plugin.getConfig().getBoolean("settings.party-auto-start",true)&&plugin.matchManager().startParty(p.getUniqueId())){p.closeInventory();return;}
        plugin.partyManager().queue(p.getUniqueId(),mode);p.closeInventory();
    }

    @EventHandler public void onDrop(PlayerDropItemEvent e){if(is(e.getItemDrop().getItemStack()))e.setCancelled(true);}
    @EventHandler public void onDrag(InventoryDragEvent e){if(e.getWhoClicked() instanceof Player p&&e.getRawSlots().stream().anyMatch(s->s<9)&&is(e.getOldCursor()))e.setCancelled(true);}

    private boolean is(ItemStack x){if(x==null||x.getType()==Material.AIR||x.getItemMeta()==null)return false;return x.getItemMeta().getPersistentDataContainer().has(key,PersistentDataType.STRING);}
    private boolean isType(ItemStack x,String type){if(!is(x))return false;return type.equals(x.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING));}
    private void mark(ItemStack x,String type){if(x==null||x.getItemMeta()==null)return;ItemMeta m=x.getItemMeta();m.getPersistentDataContainer().set(key,PersistentDataType.STRING,type);x.setItemMeta(m);}
    private ItemStack item(Material m,String name,String... lore){ItemStack x=new ItemStack(m);ItemMeta meta=x.getItemMeta();if(meta!=null){meta.setDisplayName(name);meta.setLore(List.of(lore));x.setItemMeta(meta);}return x;}
    private void fill(Inventory inv){ItemStack x=item(Material.GRAY_STAINED_GLASS_PANE," ");for(int i=0;i<27;i++)inv.setItem(i,x.clone());}
    private String pretty(KitType k){return k==KitType.SPEAR_MACE?"Spear & Mace":k.name().replace('_',' ');}

    public enum PartyMode {
        ONE_V_ONE("Party 1v1"), TWO_V_TWO("Party 2v2"), FFA("Party FFA");
        private final String displayName;
        PartyMode(String displayName){this.displayName=displayName;}
        public String displayName(){return displayName;}
    }
}