# ActionHealth
ActionHealth is a Minecraft plugin that utilizes the action bar to display entity health.

Spigot page: [Click Here](https://www.spigotmc.org/resources/action-bar-health.2661/)

# Supported versions
One jar runs on every Spigot version from 1.8 to the current release (26.3 at the time of writing), on any Java version the server uses (Java 8 or greater). Forks of Spigot, such as Paper, work too.

ActionHealth does not check the server version. It detects which API is available, so new Minecraft versions do not need a new ActionHealth release. See [compat/README.md](compat/README.md) for how this is tested.

# Dependencies
**Required**
- Nothing besides Spigot (or a fork) 1.8 or greater

**Optional**
- For region disable option:
  - WorldGuard
  - WorldEdit
  - ActionHealth is compatible with both 6 and 7
- Placeholders support:
  - PlaceholderAPI
  - MVdWPlaceholderAPI
- Supports MythicMobs (using internal name) for blacklisting
- [LanguageUtils](https://www.spigotmc.org/resources/1-7-x-1-12-language-utils.8859/) for client translations

# Config
ActionHealth is a very configurable plugin. You can almost change every aspect in the config, including style.

Default config: [Click Here](https://github.com/zeshan321/ActionHealth/blob/master/config.yml)

# Translations
[LanguageUtils](https://www.spigotmc.org/resources/1-7-x-1-12-language-utils.8859/) is supported to get the localized name of an entity but if you prefer using your own custom translations, you can use the built in system.

A list of the community made translations: [Click Here](https://github.com/zeshan321/ActionHealth/wiki/Community-Translations)

# Compiling
To compile ActionHealth, you need **JDK 17** or newer and an internet connection. Then, clone this repo, run `./gradlew clean build` and get your jar from `build/libs/ActionHealth-VERSION-all.jar`. The jar targets Java 8, so it runs on every server.

# More info
Custom styles, screenshots, command information and more can be found on the spigot page.

Spigot page: [Click Here](https://www.spigotmc.org/resources/action-bar-health.2661/)
