# Demo videos

The four Stage 2 demo videos, recorded on 2026-11-16 with `tools/demo-video` (see its README). The app ran unchanged with the real `ClaudeAdapter` and Claude Sonnet 5.5; every story turn, draft, plan, check, recap and ending in them is a live model reply. A panel on the left names the feature on screen, its type and what is happening. There is no voice-over.

| Video | What it shows | Length | File |
|---|---|---|---|
| V1 | Setting up a story in the GUI | 3:16 | `V1.mp4` |
| V2 | Playing the story in the GUI | 6:38 | `V2.mp4` |
| V3 | Memory and the ending in the GUI | 4:13 | `V3.mp4` |
| V4 | Every feature in the CLI, then the no-key error paths | 6:24 | `V4.mp4` |

Each video continues the story the one before it saved. Between V2 and V3 the story was played on off camera (`record.sh prep3`) to 10 turns with one pivotal event left, so V3 can show the summary and the suggested ending.

## Feature index

Timestamps are where the feature's panel appears (to within a second). The second column of each row is the GUI, the third the CLI.

| ID | Feature | GUI | CLI | What to look for |
|---|---|---|---|---|
| F01 | Scenario Setup | V1 1:56 | V4 2:09 | Claude drafts the five fields from an idea; the setting is mandatory (error path V1 2:16) |
| F02 | Lore Entry Management | V1 0:12 | V4 0:41 | Add, edit, search, pin; duplicate title and multi-word tag rejected (V1 0:42, 0:53; V4 1:12) |
| F03 | Character Management | V1 1:12 | V4 1:26 | Characters and one-way relationships; duplicate name and unknown target rejected (V1 1:39; V4 1:56) |
| F04 | Story Turn | V2 0:16 | V4 2:46 | Do, Say and Story modes; "Lore used this turn"; empty turn rejected (V2 1:42) |
| F05 | Pivotal Events | V1 2:35 | V4 2:32 | The plan exists only in the save file (V1 2:57); without a key planning fails and no story starts (V4 5:48) |
| F06 | Plot Steering | V2 4:09 | (hidden in every turn) | E2 goes from PENDING to REACHED after the turn that makes it happen (V2 5:11) |
| F07 | Lore Proposals | V2 1:50 | V4 4:08 | A proposal approved into the lore (V2 2:55); approve, reject, approving twice rejected (V4 4:13 to 4:20) |
| F08 | Consistency Check | V2 3:05 | (warning lines in every turn) | A turn that writes a full moon against the pinned "The red night" gets a warning and still goes through (V2 4:03) |
| F09 | Story Memory Summarization | V3 0:31 | V4 0:21 | The recap Claude wrote on load; `summary` and `summarizedUpTo` in the save file (V3 0:41); without a key the story still loads (V4 6:13) |
| F10 | Undo / Retry / Edit Turn | V2 5:25 | V4 4:25 | Undo, Retry, Edit, undo of the edit, empty edit rejected |
| F11 | Save and Load Session | V3 0:04 | V4 0:09 | Start screen and load; undo history empty after a load; loading a missing story rejected (V4 0:31) |
| F12 | Ending Generation | V3 0:57 | V4 5:14 | Early end asks first; the app suggests ending once the last event is reached (V3 2:42); THE END, Rewrite ending |

## Takes and retries

A few takes were re-recorded because the model did something legitimate that did not show the feature (no lore proposal, the narrator steering around the contradiction, the last event not yet reached). The scripts now push the story further in those cases and say so in the panel, rather than claim something that did not happen.

Three times in about 60 live turns the model ended a turn with no narration, and the app showed "Claude returned no narration. Try again." The scripts send the same turn again; V4 shows this once at 3:03. In one of those cases a lore proposal made earlier in the failed turn was still queued afterwards, which matches the documented F07 behaviour that proposals from a failed turn stay pending.
