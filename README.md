# CombatLogCommands

A Fabric server-side mod for Minecraft **26.1.2 - 26.2** that adds PvP combat tagging. No client-side mod is required — players join with a vanilla client.

## How it works

- Hitting another player, or being hit by one, puts **both** players in combat for **15 seconds**. Landing another hit refreshes the timer back to 15 seconds for both players involved.
- While in combat, a small red "Combat Tag: Xs" countdown is shown at the bottom of the screen (the action bar).
- While in combat, players can't use `/back`, `/tpa`, `/tpaccept`, `/home`, `/spawn`, or `/tpahere` (or any other command listed in the config) — attempting to do so sends a warning in chat and cancels the command, regardless of which mod added it.
- Players also can't send `/tpa <name>` or `/tpahere <name>` **to** someone who is currently in combat — the sender gets a warning that the target is mid-fight.
- If a tagged player disconnects while still in combat, they are killed **immediately**, before the disconnect completes — there's no window to camp offline and dodge the punishment. A visual-only lightning bolt strikes them for effect (no fire, no block/entity damage from it — the kill itself is what does the damage), with the vanilla thunder sound, and everyone sees "**\<player\> has logged out during combat!**" in chat.
- `/back` also has its own **30-second cooldown**, independent of combat, so it can't be spammed.
- Firework rockets have a **1.5-second cooldown while in combat** (elytra boosting included), shown as the vanilla ender-pearl-style white bar on the item — no chat spam. Every 3rd rocket, the cooldown stretches to **2.5 seconds**. No cooldown outside combat.

## Config

On first run, a config file is created at `config/combatlogcommands.json`:

```json
{
  "blockedCommands": [
    "back",
    "tpa",
    "tpaccept",
    "home",
    "spawn",
    "tpahere"
  ],
  "blockedWhenTargetInCombat": [
    "tpa",
    "tpahere"
  ],
  "combatDurationSeconds": 15.0,
  "backCooldownSeconds": 30.0,
  "fireworkCooldownSeconds": 1.5,
  "fireworkEveryThirdCooldownSeconds": 2.5,
  "playerOverrides": {}
}
```

- `blockedCommands` — commands a combat-tagged player can't use (no leading slash, case-insensitive).
- `blockedWhenTargetInCombat` — commands that can't be sent **at** a player who is in combat (first argument is treated as the target's name).
- `combatDurationSeconds` — how long the combat tag lasts per hit.
- `backCooldownSeconds` — the always-on `/back` cooldown.
- `fireworkCooldownSeconds` / `fireworkEveryThirdCooldownSeconds` — the in-combat firework rocket cooldown, and the longer one applied to every 3rd rocket.
- `playerOverrides` — per-player settings, keyed by player name (case-insensitive). Each entry may set `combatDurationSeconds`, `fireworkCooldownSeconds`, and/or `fireworkEveryThirdCooldownSeconds`; anything left out falls back to the global value. Example:

```json
"playerOverrides": {
  "Steve": {
    "combatDurationSeconds": 30.0,
    "fireworkCooldownSeconds": 5.0
  }
}
```

Old config files upgrade automatically: any missing settings are added with their defaults on server start, and your existing values are kept. After editing by hand, apply live with `/combatlog reload`.

## Admin commands

Requires op (permission level 2+):

- `/combatlog reload` — re-read the config file after editing it by hand (no restart needed).
- `/combatlog reset` — overwrite the config file with default settings.

## Requirements

- Minecraft **26.1.2 - 26.2** (Fabric)
- Fabric Loader `>= 0.19.3`
- Fabric API (matching game version)
- Java **25** on the server

## Building

```
./gradlew build
```

The output jar is written to `build/libs/`.
