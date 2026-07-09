# CombatLogCommands

A Fabric server-side mod for Minecraft **26.1.2 - 26.2** that adds PvP combat tagging. No client-side mod is required — players join with a vanilla client.

## How it works

- Hitting another player, or being hit by one, puts **both** players in combat for **15 seconds**. Landing another hit refreshes the timer back to 15 seconds for both players involved.
- While in combat, a small red "Combat Tag: Xs" countdown is shown at the bottom of the screen (the action bar).
- While in combat, players can't use `/back`, `/tpa`, `/home`, `/spawn`, or `/tpahere` (or any other command listed in the config) — attempting to do so sends a warning in chat and cancels the command, regardless of which mod added it.
- If a tagged player disconnects while still in combat, they are killed **immediately**, before the disconnect completes — there's no window to camp offline and dodge the punishment. A visual-only lightning bolt strikes them for effect (no fire, no block/entity damage from it — the kill itself is what does the damage).
- `/back` also has its own **30-second cooldown**, independent of combat, so it can't be spammed.

## Config

On first run, a config file is created at `config/combatlogcommands.json`:

```json
{
  "blockedCommands": [
    "back",
    "tpa",
    "home",
    "spawn",
    "tpahere"
  ]
}
```

Add or remove command names (no leading slash, case-insensitive) to change what's blocked during combat.

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
