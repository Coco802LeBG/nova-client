# Nova Client (Fabric 1.21.11)

Menu (Right Shift by default), auction toast with timer, recent auctions (Redo / X), gamble tracker (W/L/%, net) and a Session Profit HUD.

## Build
1. Install JDK 21 and IntelliJ IDEA.
2. Go to https://fabricmc.net/develop/template/ , pick Minecraft 1.21.11, Mojang mappings, mod id `nova`, package `com.nova`, and download the template.
3. Replace the template's `src/`, `build.gradle` and `gradle.properties` with the ones from this zip (keep the template's `gradlew` and `gradle/wrapper`).
4. Run `./gradlew build`. The jar is in `build/libs/`. Put it in `.minecraft/mods` with Fabric Loader + Fabric API.
5. If a Fabric version in gradle.properties is not found, use the latest ones listed on the template page.

## Config
`.minecraft/config/nova.json` has the chat patterns used to detect bids ("X paid you $Y") and gamble wins/losses. Change them to match your server's messages.
