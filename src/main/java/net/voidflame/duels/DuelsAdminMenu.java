package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.List;

public final class DuelsAdminMenu implements Listener {
    private final VoidFlameDuelsPlugin plugin;
    public DuelsAdminMenu(VoidFlameDuelsPlugin plugin){this.plugin=plugin;}
    public void open(Player p){
        Inventory i=Bukkit.createInventory(new Holder(),54,"§8VoidFlame §7• §dDuels Admin");
        for(int s=0;s<54;s++)i.setItem(s,item(Material.BLACK_STAINED_GLASS_PANE," "));
        button(i,10,Material.NETHER_STAR,"§d§lQUEUES","§7Total queued: §f"+plugin.queueManager().totalQueued());
        button(i,11,Material.DIAMOND_SWORD,"§d§lMATCHES","§7Active players: §f"+plugin.matchManager().totalActivePlayers());
        button(i,12,Material.ENDER_PEARL,"§a§lFFA","§7Open FFA controls.");
        button(i,13,Material.BOOK,"§e§lREPORTS","§7Open report management.");
        button(i,14,Material.GOLD_INGOT,"§6§lCOINS","§7Open coin administration.");
        button(i,15,Material.COMPARATOR,"§b§lSETTINGS","§7Open duel settings.");
        button(i,49,Material.BARRIER,"§c§lCLOSE");
        p.openInventory(i);
    }
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p)||!(e.getView().getTopInventory().getHolder() instanceof Holder))return;
        e.setCancelled(true); if(e.getRawSlot()==49){p.closeInventory();return;}
        String c=switch(e.getRawSlot()){case 12->"ffa";case 13->"reports";case 14->"coins";case 15->"settings";default->null;};
        if(c!=null){p.closeInventory();p.performCommand(c);}
    }
    private void button(Inventory i,int s,Material m,String n,String...l){i.setItem(s,item(m,n,l));}
    private ItemStack item(Material m,String n,String...l){ItemStack x=new ItemStack(m);ItemMeta z=x.getItemMeta();if(z!=null){z.setDisplayName(n);z.setLore(List.of(l));x.setItemMeta(z);}return x;}
    private static final class Holder implements InventoryHolder{public Inventory getInventory(){return null;}}
}
