package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.response.SimpleFormResponse;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bedrock-native Forms API entry points. Java clients use Paper Dialogs instead.
 */
public final class BedrockFormMenus {
    private final VoidFlameDuelsPlugin plugin;

    public BedrockFormMenus(VoidFlameDuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        SimpleForm.Builder form = SimpleForm.builder().title("VoidFlameMC")
                .content("Practice • Duels • Player tools");
        List<Runnable> actions = new ArrayList<>();
        add(form, actions, "Duels & Queues", () -> openQueue(player));
        add(form, actions, "My Statistics", () -> player.performCommand("stats"));
        add(form, actions, "Leaderboard", () -> openLeaderboard(player));
        add(form, actions, "Kit Layouts", () -> openEditorChoice(player));
        add(form, actions, "Party", () -> openParty(player));
        add(form, actions, "Settings", () -> plugin.settingsMenu().open(player));
        add(form, actions, "Free For All", () -> player.performCommand("ffa"));
        add(form, actions, "Spawn", () -> player.performCommand("spawn"));
        send(player, form, actions);
    }

    public void openQueue(Player player) {
        List<KitType> kits = enabledKits();
        SimpleForm.Builder form = SimpleForm.builder().title("Duels & Queues")
                .content(kits.isEmpty() ? "No kits are currently enabled." : "Choose a kit to view queue options.");
        List<Runnable> actions = new ArrayList<>();
        for (KitType kit : kits) {
            String name = pretty(kit);
            add(form, actions, name + " • " + plugin.queueManager().queued(kit, false)
                    + " unranked / " + plugin.queueManager().queued(kit, true) + " ranked",
                    () -> openKit(player, kit));
        }
        add(form, actions, "Main Menu", () -> openMain(player));
        send(player, form, actions);
    }

    public void openKit(Player player, KitType kit) {
        SimpleForm.Builder form = SimpleForm.builder().title(pretty(kit))
                .content("Choose a queue type or edit your layout.");
        List<Runnable> actions = new ArrayList<>();
        add(form, actions, "Unranked Queue", () -> joinQueue(player, kit, false));
        add(form, actions, "Ranked Queue", () -> joinQueue(player, kit, true));
        add(form, actions, "Edit Layout", () -> player.performCommand("kiteditor " + kit.name().toLowerCase(Locale.ROOT)));
        add(form, actions, "Back to Kits", () -> openQueue(player));
        send(player, form, actions);
    }

    public void openTargetKit(Player player, Player target) {
        List<KitType> kits = enabledKits();
        SimpleForm.Builder form = SimpleForm.builder().title("Duel " + target.getName())
                .content("Choose a kit for your duel request.");
        List<Runnable> actions = new ArrayList<>();
        for (KitType kit : kits) {
            add(form, actions, pretty(kit), () -> {
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
            });
        }
        add(form, actions, "Cancel", () -> openMain(player));
        send(player, form, actions);
    }

    public void openLeaderboard(Player player) {
        SimpleForm.Builder form = SimpleForm.builder().title("Leaderboard")
                .content("Choose a ranking category.");
        List<Runnable> actions = new ArrayList<>();
        add(form, actions, "ELO", () -> player.performCommand("stats top elo"));
        add(form, actions, "Wins", () -> player.performCommand("stats top wins"));
        add(form, actions, "Losses", () -> player.performCommand("stats top losses"));
        add(form, actions, "Streak", () -> player.performCommand("stats top streak"));
        add(form, actions, "Main Menu", () -> openMain(player));
        send(player, form, actions);
    }

    public void openEditorChoice(Player player) {
        List<KitType> kits = enabledKits();
        SimpleForm.Builder form = SimpleForm.builder().title("Kit Layouts")
                .content("Choose a kit whose layout you want to edit.");
        List<Runnable> actions = new ArrayList<>();
        for (KitType kit : kits) {
            add(form, actions, pretty(kit),
                    () -> player.performCommand("kiteditor " + kit.name().toLowerCase(Locale.ROOT)));
        }
        add(form, actions, "Back", () -> openMain(player));
        send(player, form, actions);
    }

    public void openParty(Player player) {
        SimpleForm.Builder form = SimpleForm.builder().title("Party").content("Party actions");
        List<Runnable> actions = new ArrayList<>();
        add(form, actions, "Create Party", () -> player.performCommand("party create"));
        add(form, actions, "Accept Invite", () -> player.performCommand("party accept"));
        add(form, actions, "Party Info", () -> player.performCommand("party info"));
        add(form, actions, "Queue 1v1", () -> player.performCommand("party queue 1v1"));
        add(form, actions, "Queue 2v2", () -> player.performCommand("party queue 2v2"));
        add(form, actions, "Queue FFA", () -> player.performCommand("party queue ffa"));
        add(form, actions, "Leave Party", () -> player.performCommand("party leave"));
        add(form, actions, "Main Menu", () -> openMain(player));
        send(player, form, actions);
    }

    public void openMatchResult(Player player, String result, String opponent, String kit, String arena, long durationSeconds) {
        String duration = String.format(Locale.ROOT, "%02d:%02d",
                Math.max(0L, durationSeconds) / 60L, Math.max(0L, durationSeconds) % 60L);
        SimpleForm.Builder form = SimpleForm.builder().title("Match " + result)
                .content("Opponent: " + opponent + "\nKit: " + kit + "\nArena: " + arena + "\nDuration: " + duration);
        List<Runnable> actions = new ArrayList<>();
        add(form, actions, "Rematch", () -> player.performCommand("rematch"));
        add(form, actions, "Spawn", () -> player.performCommand("spawn"));
        add(form, actions, "Main Menu", () -> openMain(player));
        send(player, form, actions);
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
        var registration = Bukkit.getServicesManager().getRegistration(net.voidflame.core.api.KitService.class);
        var service = registration == null ? null : registration.getProvider();
        for (KitType kit : KitType.values()) {
            String key = kit.name().toLowerCase(Locale.ROOT);
            if (!plugin.getConfig().getBoolean("gui.kits." + key + ".enabled", true)) continue;
            boolean configured = plugin.getConfig().getConfigurationSection("kits." + key + ".items") != null;
            boolean external = service != null && service.isEnabled(key);
            if (configured || external) result.add(kit);
        }
        return result;
    }

    private String pretty(KitType kit) {
        return kit == KitType.SPEAR_MACE ? "Spear & Mace" : kit.name().replace('_', ' ');
    }

    private void add(SimpleForm.Builder form, List<Runnable> actions, String label, Runnable action) {
        form.button(label);
        actions.add(action);
    }

    private void send(Player player, SimpleForm.Builder form, List<Runnable> actions) {
        form.validResultHandler((SimpleFormResponse response) -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size()) run(player, actions.get(index));
        });
        sendForm(player, form);
    }

    public boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("floodgate");
    }

    public void sendForm(Player player, SimpleForm.Builder form) {
        try {
            if (!available()) {
                player.sendMessage("§cBedrock Forms require Floodgate to be installed.");
                return;
            }
            boolean sent = FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
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
