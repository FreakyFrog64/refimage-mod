# Reference Image Overlay — get a working .jar

This folder is a complete, ready-to-build NeoForge 1.21.1 mod project
(official MDK template + the mod's source already dropped in). You do
**not** need to edit any code — just get it compiled.

I can't compile it myself: this chat runs in a sandboxed environment
that's only allowed to reach a short list of websites, and Minecraft's
build tools aren't on that list (I tried — it gets blocked). So here
are two ways for *you* to get an actual .jar, easiest first.

---

## Path A — GitHub Actions builds it for you (no installs, ~10 minutes)

This uses GitHub's free servers to do the compiling. You never touch
Java or Gradle.

1. **Make a GitHub account** if you don't have one: https://github.com/signup

2. **Create a new repository.**
   - Go to https://github.com/new
   - Name it anything, e.g. `refimage-mod`
   - Leave it "Public", don't add a README/gitignore/license (we already have files)
   - Click "Create repository"

3. **Upload this entire folder's contents to that repo.**
   - On the new repo's page, click "uploading an existing file"
   - Drag in every file and folder from this project (`build.gradle`,
     `gradle.properties`, `gradlew`, `gradlew.bat`, `settings.gradle`,
     `TEMPLATE_LICENSE.txt`, the whole `gradle/` folder, the whole
     `src/` folder, and the whole `.github/` folder — GitHub's
     drag-and-drop keeps folder structure if you drag the folders
     themselves)
   - Scroll down, click "Commit changes"

   > If GitHub's uploader won't let you drag folders (browser-dependent),
   > install GitHub Desktop instead (https://desktop.github.com) — it
   > lets you point at this unzipped folder directly, publish it as a
   > new repo, and push in a few clicks.

4. **Wait for the build.** Click the "Actions" tab at the top of your
   repo. You'll see a workflow run start automatically (it's already
   configured — `.github/workflows/build.yml`). It takes a few minutes
   the first time. A green checkmark means it succeeded.

5. **Download your jar.** Click into that finished run, scroll to
   "Artifacts" at the bottom, download `refimage-mod-jar`. Unzip it —
   inside is your `.jar` file.

6. **Install it.** Drop the `.jar` into your Minecraft instance's
   `mods` folder (same place your other NeoForge 1.21.1 mods live),
   and launch.

If the run shows a red X instead of a checkmark, click into it to see
which step failed and paste me the error — that tells us exactly what
to fix, which is much easier than debugging blind.

---

## Path B — build it locally

Only do this if you want to actually edit/rebuild the code yourself
later, or Path A doesn't work for some reason.

1. **Install JDK 21.** Get the Microsoft build: https://learn.microsoft.com/en-us/java/openjdk/download#openjdk-21
   During install, just accept the defaults.

2. **Install IntelliJ IDEA Community Edition** (free): https://www.jetbrains.com/idea/download
   Accept the defaults during install.

3. **Open this folder in IntelliJ.**
   - Launch IntelliJ → "Open" → select this project folder
     (the one containing `build.gradle`)
   - IntelliJ will notice it's a Gradle project and ask to import it —
     say yes. A progress bar appears bottom-right; let it finish (this
     downloads Minecraft + mappings + NeoForge, can take 10-20 minutes
     and a few GB the first time)

4. **Build the jar.**
   - Open the "Gradle" panel (icon on the right edge of the window,
     looks like an elephant)
   - Navigate: `refimage → Tasks → build → build`, double-click it
   - When it finishes, your jar is in `build/libs/` inside this folder

5. **(Optional) Test it without building a jar first.**
   - In the same Gradle panel: `refimage → Tasks → neoforge → runClient`
   - This launches a throwaway Minecraft instance with the mod already
     loaded, so you can try the `/refimg` commands immediately

6. **Install it** the same way as step 6 in Path A.

---

## Using the mod once it's installed

All commands start with `/refimg`. Type `/refimg load ` then paste a
**direct** image link (e.g. `i.imgur.com/xxxx.png` — not the imgur
page URL) and hit enter. Then `/refimg here` to drop it where you're
standing. `/refimg size <w> <h>`, `/refimg opacity <0-100>`, and
`/refimg rotate <yaw> <pitch>` adjust it from there. Full list in
`src/main/java/com/szaros/refimage/client/RefImageCommands.java`.

## If something doesn't compile

The exact error message matters a lot here — paste it back to me
rather than describing it, and I can usually pinpoint the fix
immediately even without running it myself.
