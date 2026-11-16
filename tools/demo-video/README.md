# Demo video recording

Records the four Stage 2 demo videos of the real app in a headless Linux session (a cloud container or
any machine with Xvfb). The app runs unchanged, with the real `ClaudeAdapter` and Claude Sonnet 5.5;
these scripts only click, type and wait the way a person would, and show a feature panel on the left.
They are recording aids, not part of the app, so they live outside `src/` and the Maven build.

| File | What it does |
|---|---|
| `record.sh` | Starts Xvfb (1536x864, recorded and scaled to 1920x1080), openbox and ffmpeg, runs one video, writes `out/<video>.mp4` and `out/<video>-timeline.tsv`. |
| `src/.../FeaturePanel.java` | The left panel: feature ID, name, AI / Hybrid / Deterministic, what is happening. Logs each feature's start time. |
| `src/.../DemoDirector.java` | Drives `StoryGUI` for V1 to V3 with `java.awt.Robot` (real mouse clicks on the app's own buttons, menus, tabs and list rows). `prep3` plays the V2 story on off camera for V3. |
| `cli_driver.py` | Drives `StoryCLI` for V4 on a pseudo-terminal inside an xterm, typing each command and waiting for its prompt. |

The scripts follow the plan in the "Stage 2 demo video plan" doc. They react to what the model actually
does (whether it proposed lore, whether the consistency check warned, which pivotal event is next) and
say so in the panel, so a take never claims something that did not happen. When a feature does not show
on the first try (no lore proposal yet, the narrator writing around a contradiction, the last pivotal event
not reached), the scripts push the story further a few times. A turn that fails with a model error is sent
again, up to twice, and the panel says so.

## Run

Needs Java 21, Maven, Xvfb, ffmpeg with libx264, xterm, python3 and openbox (`apt-get install openbox`).
The key comes from `ANTHROPIC_API_KEY`, or from `STORYTELLER_API_KEY` where the environment strips the
first name. It is handed to the app's process only and is never printed.

```
tools/demo-video/record.sh reset    # fresh save folder with samples/sample-ashfall.json
tools/demo-video/record.sh V1       # setting up a story (F02, F03, F01, F05)
tools/demo-video/record.sh V2       # playing it (F04, F07, F08, F06, F10)
tools/demo-video/record.sh prep3    # off camera: 10+ turns, one pivotal event left
tools/demo-video/record.sh V3       # memory and the ending (F11, F09, F12)
tools/demo-video/record.sh V4       # the CLI, including the no-key error paths
```

Run them in this order: each video continues the story the previous one saved. Saves go to
`tools/demo-video/work/demo-saves/` and recordings to `tools/demo-video/out/` (both gitignored).
Lines in `out/<video>-timeline.tsv` read `m:ss <tab> F04 <tab> Story Turn (Hybrid)`: the start of each
feature segment, for the report's demonstration index.
