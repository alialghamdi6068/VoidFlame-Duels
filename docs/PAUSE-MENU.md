# Native ESC menu compatibility

VoidFlame-Duels exposes permission-protected commands that can be called from a native Minecraft dialog. For the actual button inside the vanilla ESC/pause screen, install a dialog-compatible menu plugin such as [DonutQuickActions](https://modrinth.com/plugin/donutquickactions) on Paper 1.21.7–1.21.11 / 26.2, then copy `extras/DonutQuickActions/config.yml` into its generated config and restart the server.

The template adds a **VoidFlame** entry to the native pause screen. The **Admin Panel** action is permission-gated by `voidflame.duels.admin`; the command itself is also permission-protected by VoidFlame-Duels, so manually typing it does not bypass authorization.

This is a server-side dialog integration: players do not need a client mod. All clients must support Minecraft's native dialog/pause-screen additions (introduced in 1.21.6); older clients may not show the pause-screen entry. Keep ViaVersion/client compatibility limitations in mind.

The Kit Editor action is restricted by `voidflame.kits.edit`. If your kit editor is provided by VoidFlame-Kits rather than this plugin, ensure that plugin is installed and grants the same permission.
