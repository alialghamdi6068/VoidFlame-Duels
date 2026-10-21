package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.response.SimpleFormResponse;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Bedrock-native Forms API entry points. Java clients use Paper Dialogs instead.
 */
public final class BedrockFormMenus {
    private final VoidFlameDuelsPlugin plugin;

    public BedrockFormMenus(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        SimpleForm.Builder form = SimpleForm.builder()
                .title("VoidFlameMC")
                .content("Practice • Duels • Player tools");
        form.button("Duels & Queues", response -> run(player, () -> openQueue(player)));
        form.button("My Statistics", response -> run(player, () -> player.performCommand("stats")));
        form.button("Leaderboard", response -> run(player, () -> player.performCommand("leaderboard")));
        form.button("Party", response -> run(player, () -> openParty(player)));
        form.button("Settings", response -> run(player, () -> plugin.settingsMenu().open(player)));
        form.button("Free For All", response -> run(player, () -> player.performCommand("ffa")));
        form.button("Spawn", response -> run(player, () -> player.performCommand("spawn")));
        send(player, form);
    }

    public void openQueue(Player player) {
        List<KitType> kits = enabledKits();
        SimpleForm.Builder form = SimpleForm.builder()
                .title("Duels & Queues")
                .content(kits.isEmpty() ? "No kits are currently enabled." : "Choose a kit to view queue options.");
        for (KitType kit : kits) {
            String name = pretty(kit);
            form.button(name + "  •  " + plugin.queueManager().queued(kit, false) + " unranked / "
                    + plugin.queueManager().queued(kit, true) + " ranked", response -> run(player, () -> openKit(player, kit)));
        }
        form.button("Main Menu", response -> run(player, () -> openMain(player)));
        send(player, form);
    }

    public void openKit(Player player, KitType kit) {
        SimpleForm.Builder form = SimpleForm.builder()
                .title(pretty(kit))
                .content("Choose a queue type or edit your layout.");
        form.button("Unranked Queue", response -> run(player, () -> joinQueue(player, kit, false)));
        form.button("Ranked Queue", response -> run(player, () -> joinQueue(player, kit, true)));
        form.button("Edit Layout", response -> run(player, () -> player.performCommand("kiteditor " + kit.name().toLowerCase(Locale.ROOT))));
        form.button("Back to Kits", response -> run(player, () -> openQueue(player)));
        send(player, form);
    }

    public void openTargetKit(Player player, Player target) {
        List<KitType> kits = enabledKits();
        SimpleForm.Builder form = SimpleForm.builder()
                .title("Duel " + target.getName())
                .content("Choose a kit for your duel request.");
        for (KitType kit : kits) {
            form.button(pretty(kit), response -> run(player, () -> {
                if (!target.isOnline()) {
                    player.sendMessage(plugin.message("player-not-found"));
                    return;
                }
                if (!plugin.requests().send(player, target, kit)) {
                    player.sendMessage(plugin.message("duel-unavailable"));
                    return;
                }
                player.sendMessage(plugin.message("duel-sent").replace("<player>", target.getName()));
                target.sendMessage(plugin.message("duel-received").replace("<player>", player.getName()).replace("<kit>", pretty(kit)));
            }));
        }
        form.button("Cancel", response -> run(player, () -> openMain(player)));
        send(player, form);
    }

    public void openLeaderboard(Player player) {
        SimpleForm.Builder form = SimpleForm.builder()
                .title("Leaderboard")
                .content("Choose a ranking category.");
        form.button("ELO", response -> run(player, () -> player.performCommand("stats top elo")));
        form.button("Wins", response -> run(player, () -> player.performCommand("stats top wins")));
        form.button("Losses", response -> run(player, () -> player.performCommand("stats top losses")));
        form.button("Streak", response -> run(player, () -> player.performCommand("stats top streak")));
        form.button("Main Menu", response -> run(player, () -> openMain(player)));
        send(player, form);
    }

    public void openEditorChoice(Player player) {
        List<KitType> kits = enabledKits();
        SimpleForm.Builder form = SimpleForm.builder()
                .title("Kit Layouts")
                .content("Choose a kit whose layout you want to edit.");
        for (KitType kit : kits) {
            form.button(pretty(kit), response -> run(player, () -> player.performCommand("kiteditor " + kit.name().toLowerCase(Locale.ROOT))));
        }
        form.button("Back", response -> run(player, () -> openMain(player)));
        send(player, form);
    }

    public void openParty(Player player) {
        SimpleForm.Builder form = SimpleForm.builder()
                .title("Party")
                .content("Party actions");
        form.button("Create Party", response -> run(player, () -> player.performCommand("party create")));
        form.button("Accept Invite", response -> run(player, () -> player.performCommand("party accept")));
        form.button("Party Info", response -> run(player, () -> player.performCommand("party info")));
        form.button("Queue 1v1", response -> run(player, () -> player.performCommand("party queue 1v1")));
        form.button("Queue 2v2", response -> run(player, () -> player.performCommand("party queue 2v2")));
        form.button("Queue FFA", response -> run(player, () -> player.performCommand("party queue ffa")));
        form.button("Leave Party", response -> run(player, () -> player.performCommand("party leave")));
        form.button("Main Menu", response -> run(player, () -> openMain(player)));
        send(player, form);
    }

    public void openMatchResult(Player player, String result, String opponent) {
        SimpleForm.Builder form = SimpleForm.builder()
                .title("Match " + result)
                .content("Opponent: " + opponent);
        form.button("Rematch", response -> run(player, () -> player.performCommand("rematch")));
        form.button("Spawn", response -> run(player, () -> player.performCommand("spawn")));
        form.button("Main Menu", response -> run(player, () -> openMain(player)));
        send(player, form);
    }

    private void joinQueue(Player player, KitType kit, boolean ranked) {
        if (plugin.queueManager().join(player, kit, ranked)) {
            player.sendMessage(plugin.message("joined-queue")
                    .replace("<kit>", pretty(kit))
                    .replace("<type>", ranked ? "Ranked" : "Unranked"));
        } else {
            player.sendMessage(plugin.message("already-queued"));
        }
    }

    private List<KitType> enabledKits() {
        List<KitType> result = new ArrayList<>();
        for (KitType kit : KitType.values()) {
            String key = kit.name().toLowerCase(Locale.ROOT);
            if (!plugin.getConfig().getBoolean("gui.kits." + key + ".enabled", true)) continue;
            boolean configured = plugin.getConfig().getConfigurationSection("kits." + key + ".items") != null;
            var service = Bukkit.getServicesManager().getRegistration(net.voidflame.core.api.KitService.class);
            boolean external = service != null && service.getProvider().isEnabled(key);
            if (configured || external) result.add(kit);
        }
        return result;
    }

    private String pretty(KitType kit) {
        return kit == KitType.SPEAR_MACE ? "Spear & Mace" : kit.name().replace('_', ' ');
    }

    private void send(Player player, SimpleForm.Builder form) {
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
                player.sendMessage("§cBedrock Forms require Floodgate to be installed.");
                return;
            }
            SimpleForm built = form.build();
            boolean sent = FloodgateApi.getInstance().sendForm(player.getUniqueId(), built);
            if (!sent) player.sendMessage("§cCould not open the Bedrock form. Please try again.");
        } catch (LinkageError | RuntimeException error) {
            plugin.getLogger().warning("Could not send Bedrock form: " + error.getMessage());
            player.sendMessage("§cCould not open this menu right now.");
        }
    }

    private void run(Player player, Runnable action) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) action.run();
        });
    }
}
