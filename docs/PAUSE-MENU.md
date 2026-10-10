# VoidFlame Practice scoreboard and native ESC menu

## TAB scoreboard

The repository includes `extras/TAB/scoreboard-section.yml`. Merge its `scoreboard:` section into `plugins/TAB/config.yml`; do not overwrite the rest of your TAB configuration. Then run `/tab reload` or restart.

If PlaceholderAPI is installed, VoidFlame-Duels registers these cached placeholders:
- `%voidflame_rank%`
- `%voidflame_wins%`
- `%voidflame_losses%`
- `%voidflame_streak%`
- `%voidflame_beststreak%`
- `%voidflame_elo%`
- `%voidflame_coins%`
- `%voidflame_mode%`

The scoreboard uses VoidFlame's purple/cyan/gold palette and leaves TAB in control of the sidebar and scoreboard teams, avoiding interference with rank prefixes and nametags.

## Native ESC menu and admin visibility

Install DonutQuickActions on a compatible Paper server, then copy `extras/DonutQuickActions/config.yml` into its generated config and restart. The admin action uses `voidflame.duels.admin`; the command and admin GUI independently enforce the same permission.

Set the permission on the intended staff group through LuckPerms, without granting OP:
`/lp group admin permission set voidflame.duels.admin true`

Replace `admin` with the exact LuckPerms group name you use. This permission is not granted by VoidFlame-Duels by default. Test the dialog as both a staff account and a normal account after installing/restarting the menu plugin.

## Kits

The built-in config includes Sword, Axe, UHC, Mace, Spear Mace, Crystal, Netherite Pot, SMP, Diamond SMP, TNT Minecart LT and TNT Minecart HT. Kits are defined in `src/main/resources/config.yml`; when VoidFlame-Kits is installed and provides the KitService, its editor is used for saved layouts.
