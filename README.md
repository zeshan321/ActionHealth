# ActionHealth

A Minecraft plugin that shows the health of players and mobs in the action bar, when you hit them or look at them.

[Spigot page](https://www.spigotmc.org/resources/action-bar-health.2661/) | [Download](https://github.com/zeshan321/ActionHealth/releases/latest) | [Default config](src/main/resources/config.yml) | [Report a bug](https://github.com/zeshan321/ActionHealth/issues)

![ActionHealth](http://i.imgur.com/QBMd4FM.jpg)

## Supported versions

One jar runs on Spigot 1.8 to 26.3, and on forks such as Paper and Folia. It runs on Java 8 or newer, so use the Java version that your server already runs.

ActionHealth does not check the server version. It checks which features the server has. New Minecraft versions should not need an update. Version 3.8.1 was tested on 27 Spigot versions, 5 Paper versions and 2 Folia versions, with bots that read the action bar. [How the tests work](compat/README.md).

## Features

- **Health on hit.** When you hit a player or a mob, its health shows in your action bar. Arrows and other projectiles count too.
- **Health on look.** When you look at an entity, its health shows. On 1.13.2+, any part of the hitbox counts, so large mobs and ModelEngine models work.
- **Styles.** Show hearts, bars, lines or numbers. Colors can be `&` codes or hex colors such as `&#4fdfc4`. Servers before 1.16 show the closest legacy color.
- **Health numbers and colors.** Show health with up to 3 decimals, for example `19.5`. `{healthcolor}` changes the color with the health that is left, for example green above 75% and red below 25%.
- **Absorption.** Absorption health shows as extra icons after the health icons, or as a number with `{absorption}`.
- **Action messages.** See when an enemy that you fought drinks a potion, eats a golden apple or uses an ender pearl. This is off by default.
- **Toggle.** Players can turn ActionHealth off for themselves. The choice can stay after they log out. With `Enabled By Default: false`, players see nothing until they turn it on.
- **Filters.** Turn ActionHealth off in worlds or WorldGuard regions, or for players, mobs, NPCs, invisible entities and spectators. Use a blacklist or a whitelist of names.
- **Translations.** Rename mobs in the config, or show mob names in each player's client language.
- **Config updates.** When an update adds options, ActionHealth adds them to the end of your `config.yml`, with their comments. The rest of the file stays the same.
- **Update check.** Once a day, ActionHealth asks spigotmc.org for the latest version number. A new version is logged in the console, and operators get a message when they join. Turn it off with `Update Check: false`.

## Install

1. Download the jar from the [latest release](https://github.com/zeshan321/ActionHealth/releases/latest) or the [Spigot page](https://www.spigotmc.org/resources/action-bar-health.2661/).
2. Put the jar in the `plugins` folder of your server.
3. Restart the server.
4. Edit `plugins/ActionHealth/config.yml`, then run `/actionhealth reload`.

## Commands and permissions

| Command or permission | What it does |
| --- | --- |
| `/actionhealth reload` | Reloads the config. Needs `ActionHealth.Reload`, which operators have by default. |
| `/actionhealth toggle` | Turns the health display on or off for you. Needs `ActionHealth.Toggle`, which all players have by default. |
| `ActionHealth.Health` | Lets a player see health messages. Applies only when `Use Permissions` is `true`. |
| `ActionHealth.Update` | Tells a player about a new version when they join. Operators have it by default. |

The command has tab completion. The command messages are in the config, so you can translate them.

## Placeholders

Use these in the health message:

| Placeholder | Shows |
| --- | --- |
| `{name}` | The name of the player or mob |
| `{displayname}` | The display name or custom name |
| `{health}` and `{maxhealth}` | Current and maximum health, with the decimals from `Health Decimals` |
| `{healthcolor}` | The color from `Health Colors` for the health that is left |
| `{percenthealth}` | Health left as a percentage |
| `{absorption}` | Absorption health |
| `{usestyle}` | The health icons from the config |
| `{opponentlastdamage}` | The last damage that the target took |

If another plugin replaces a placeholder first, add `ah` in front, for example `{ahhealth}`.

PlaceholderAPI placeholders work in the message and in the health icons. They use the attacking player. To use the attacked player, add `ATTACKEDPLAYER_`, for example `%ATTACKEDPLAYER_player_exp%`.

## Optional plugins

ActionHealth needs no other plugins. It uses these plugins when they are installed:

| Plugin | What ActionHealth does with it |
| --- | --- |
| WorldGuard 6 or 7, with WorldEdit | Turns ActionHealth off in the regions from `Disabled regions` |
| PlaceholderAPI, MVdWPlaceholderAPI | Fills in their placeholders in the message |
| MythicMobs 4 or 5 | Blacklists or whitelists mobs by their internal name |
| ModelEngine | Shows health when you look at or hit a model. Tested with ModelEngine R3. |
| mcMMO | Shows the correct mob name when mcMMO health bars are on |
| [LanguageUtils](https://www.spigotmc.org/resources/1-7-x-1-12-language-utils.8859/) | Shows mob names in each player's client language |

## Translations

Rename mobs with the `Name Change` option in the config. The [translations](translations) folder has community translations for 12 languages. You can share yours with a pull request.

## Build from source

You need JDK 17 or newer.

```sh
./gradlew clean build
```

The build also runs the unit tests. The jar is `build/libs/ActionHealth-<version>-all.jar`. It targets Java 8, so it runs on every server. To test a change on real servers, see [compat/README.md](compat/README.md).

## License

[MIT](LICENSE)
