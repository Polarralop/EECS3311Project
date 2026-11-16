#!/usr/bin/env python3
"""Drives the real StoryCLI for demo video V4, typing commands into it the way a person would.

Run by record.sh. The outer call opens an xterm on the right of the virtual screen and runs this
script again inside it ("inner"). Inside, the script starts the feature panel on the left (FeaturePanel,
reading commands on stdin), starts the CLI on a pseudo-terminal, shows its output in the xterm, and
types each command a character at a time, waiting for the CLI's prompts. Everything the CLI prints
comes from the app itself; the script only reacts to it (for example, which proposal ids it lists).

The API key is passed to the CLI through the environment and never printed. The no-key part
relaunches the CLI with ANTHROPIC_API_KEY set to an empty string.
"""
import os
import pty
import re
import select
import shlex
import subprocess
import sys
import time

PANEL_W = 400  # FeaturePanel.WIDTH


def outer(cp, log):
    cmd = ["xterm", "-geometry", "122x44+%d+0" % PANEL_W, "-fa", "DejaVu Sans Mono", "-fs", "12",
           "-bg", "#151030", "-fg", "#ECE9FB", "-cr", "#A99BFF", "-bc", "+sb", "-b", "14",
           "-xrm", "XTerm*cursorBlink: true", "-title", "StoryCLI",
           "-e", sys.executable, os.path.abspath(__file__), "inner", cp, log]
    result = subprocess.run(cmd)
    status_file = log + ".status"
    if os.path.exists(status_file):
        return int(open(status_file).read().strip() or "1")
    return result.returncode


class Cli:
    """The CLI on a pseudo-terminal; its output is copied to this terminal as it arrives."""

    def __init__(self, cp, key):
        env = dict(os.environ)
        env["ANTHROPIC_API_KEY"] = key
        env.pop("STORYTELLER_API_KEY", None)
        # The container's JVM settings (proxy, trust store) go on the command line instead of
        # JAVA_TOOL_OPTIONS, so the JVM does not print its "Picked up" banner into the recording.
        jvm_opts = shlex.split(env.pop("JAVA_TOOL_OPTIONS", ""))
        pid, fd = pty.fork()
        if pid == 0:
            os.execvpe("java", ["java"] + jvm_opts + ["-cp", cp, "ca.yorku.storyteller.ui.StoryCLI"], env)
        self.pid, self.fd = pid, fd
        self.buf = ""
        self.mark = 0

    def pump(self, timeout=0.05):
        r, _, _ = select.select([self.fd], [], [], timeout)
        if not r:
            return False
        try:
            data = os.read(self.fd, 4096)
        except OSError:
            return False
        if not data:
            return False
        text = data.decode("utf-8", "replace")
        self.buf += text
        sys.stdout.write(text)
        sys.stdout.flush()
        return True

    def expect(self, pattern, timeout=240):
        """Waits until the output after the last match contains pattern; returns the match."""
        end = time.time() + timeout
        rx = re.compile(pattern)
        while time.time() < end:
            m = rx.search(self.buf, self.mark)
            if m:
                self.mark = m.end()
                return m
            self.pump(0.1)
        raise TimeoutError("Timed out waiting for %r" % pattern)

    def idle(self, seconds):
        end = time.time() + seconds
        while time.time() < end:
            self.pump(0.05)

    def type(self, text, enter=True, cps=22):
        for ch in text:
            os.write(self.fd, ch.encode())
            self.idle(1.0 / cps)
        self.idle(0.35)
        if enter:
            os.write(self.fd, b"\r")
        self.idle(0.2)

    def command(self, text, wait_prompt=True, pause=1.6):
        """Types a command at the "> " prompt and waits for the next prompt."""
        self.expect(r"(^|\n)> $|\n> ", 300) if wait_prompt else None
        self.type(text)
        self.idle(pause)

    def answer(self, prompt_regex, text, pause=0.6):
        self.expect(prompt_regex)
        self.idle(0.5)
        self.type(text)
        self.idle(pause)

    def wait_exit(self, timeout=20):
        end = time.time() + timeout
        while time.time() < end:
            self.pump(0.1)
            done, _ = os.waitpid(self.pid, os.WNOHANG)
            if done:
                break


class Panel:
    def __init__(self, cp, log):
        env = dict(os.environ)
        env.pop("ANTHROPIC_API_KEY", None)
        env.pop("STORYTELLER_API_KEY", None)
        self.proc = subprocess.Popen(
            ["java", "-cp", cp, "ca.yorku.storyteller.ui.FeaturePanel", "864", log],
            stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, text=True, env=env)

    def send(self, *fields):
        self.proc.stdin.write("|".join(f.replace("|", "/").replace("\n", " ") for f in fields) + "\n")
        self.proc.stdin.flush()

    def video(self, title):
        self.send("video", title)

    def feature(self, fid, name, kind, about):
        self.send("feature", fid, name, kind, about)

    def step(self, text):
        self.send("step", text)

    def close(self):
        self.proc.stdin.close()
        self.proc.wait(timeout=10)


def script(cp, log):
    key = os.environ.get("ANTHROPIC_API_KEY", "")
    panel = Panel(cp, log)
    time.sleep(2.5)
    panel.video("V4 The CLI")
    panel.feature("", "The CLI", "",
                  "StoryCLI uses the same StoryController as the GUI. It only reads commands and prints "
                  "results. Claude Sonnet 5.5 is the storyteller; the key is never shown.")
    sys.stdout.write("\033[1m$ java ca.yorku.storyteller.ui.StoryCLI    (what mvn exec:java@cli runs)\033[0m\n")
    sys.stdout.flush()
    cli = Cli(cp, key)
    cli.expect(r"Type \"help\" for commands\.")
    cli.idle(1.0)
    panel.step("help lists every command.")
    cli.command("help", pause=4)

    panel.feature("F11", "Save and Load Session", "Deterministic",
                  "Stories are saved to JSON files and read back. The undo history is not saved.")
    panel.step("sessions lists the saves; load sample-ashfall opens the story from the GUI videos.")
    cli.command("sessions", pause=2.5)
    cli.command("load sample-ashfall", pause=0.5)
    cli.expect(r"Loaded")

    panel.feature("F09", "Story Memory Summarization", "AI",
                  "On load, Claude writes a recap of the story so far from the running summary and recent turns.")
    panel.step("The recap under the Loaded line was written by Claude.")
    cli.expect(r"\n> ", 300)
    cli.mark -= 3
    cli.idle(6)
    panel.feature("F11", "Save and Load Session", "Deterministic",
                  "Stories are saved to JSON files and read back. The undo history is not saved.")
    panel.step("Error path: undo after a load. The history starts empty.")
    cli.command("undo", pause=3)
    panel.step("Error path: loading a story that does not exist.")
    cli.command("load nosuch", pause=3)
    panel.feature("F09", "Story Memory Summarization", "AI",
                  "Older turns are folded into the summary; turns prints the whole story, ending included.")
    panel.step("turns prints the story to its ending from V3.")
    cli.command("turns", pause=5)

    panel.feature("F02", "Lore Entry Management", "Deterministic",
                  "Add, list, search, pin and show lore entries. Plain validation, no AI.")
    panel.step("new opens an empty story; lore add creates a pinned entry.")
    cli.command("new", pause=1.5)
    cli.command("lore add", pause=0.3)
    cli.answer(r"Title: $", "The Drowned Archive")
    cli.answer(r"Content: $", "A library sunk beneath the harbour, reachable only at low tide.")
    cli.answer(r"Tags \(comma separated, optional\): $", "place, library")
    cli.answer(r"Pinned\? \(y/n\): $", "y", pause=1.5)
    panel.step("lore list, lore search harbour.")
    cli.command("lore list", pause=2)
    cli.command("lore search harbour", pause=2.5)
    panel.step("Unpin and pin again, then show the entry.")
    cli.command("lore unpin L1", pause=1.5)
    cli.command("lore pin L1", pause=1.5)
    cli.command("lore show L1", pause=3)
    panel.step("Error path: a tag must be a single word.")
    cli.command("lore add", pause=0.3)
    cli.answer(r"Title: $", "Test")
    cli.answer(r"Content: $", "A test entry.")
    cli.answer(r"Tags \(comma separated, optional\): $", "not valid!")
    cli.answer(r"Pinned\? \(y/n\): $", "n", pause=3.5)

    panel.feature("F03", "Character Management", "Deterministic",
                  "Characters with traits, and one-way relationships between them. Validated, no AI.")
    panel.step("char add Ines, then Bram.")
    cli.command("char add", pause=0.3)
    cli.answer(r"Name: $", "Ines")
    cli.answer(r"Description: $", "An archivist who can hold her breath for minutes")
    cli.answer(r"Traits \(comma separated\): $", "patient, proud", pause=1.2)
    cli.command("char add", pause=0.3)
    cli.answer(r"Name: $", "Bram")
    cli.answer(r"Description: $", "A harbour diver")
    cli.answer(r"Traits \(comma separated\): $", "brave", pause=1.2)
    panel.step("rel add: Ines to Bram, rival. Then char show Ines.")
    cli.command("rel add", pause=0.3)
    cli.answer(r"From character: $", "Ines")
    cli.answer(r"To character: $", "Bram")
    cli.answer(r"Type \(for example sister, rival\): $", "rival")
    cli.answer(r"Notes \(optional\): $", "", pause=1.5)
    cli.command("char show Ines", pause=3)
    panel.step("Error path: a relationship to a character who does not exist.")
    cli.command("rel add", pause=0.3)
    cli.answer(r"From character: $", "Ines")
    cli.answer(r"To character: $", "Nobody")
    cli.answer(r"Type \(for example sister, rival\): $", "friend")
    cli.answer(r"Notes \(optional\): $", "", pause=3.5)

    panel.feature("F01", "Scenario Setup", "Hybrid",
                  "Claude drafts a scenario from an idea (AI). Each field is offered as a default: Enter keeps "
                  "it, or type over it.")
    panel.step("start, then an idea for Claude to draft from.")
    cli.command("start", pause=0.3)
    cli.answer(r"Story idea[^\n]*: $", "Ines must recover a forbidden book before the tide turns.", pause=0.5)
    cli.expect(r"Use this draft\?[^\n]*: $")
    panel.step("The draft Claude wrote. Keep it and press Enter at each field.")
    cli.idle(6)
    cli.type("y")
    for label in ["Title", "Premise", "Tone", "Setting", "Opening situation"]:
        cli.expect(re.escape(label) + r"[^\n]*: $")
        cli.idle(1.2)
        cli.type("")

    panel.feature("F05", "Pivotal Events", "Hybrid",
                  "Starting the story makes Claude plan up to eight hidden turning points (AI); the app stores "
                  "and tracks them. The CLI never prints them.")
    panel.step("Plot basis, kept hidden. Then Claude plans the events.")
    cli.answer(r"Plot basis[^\n]*: $", "Bram should betray her at the worst moment.", pause=0.5)
    cli.expect(r"has begun\.|Error:")
    panel.step("The story has begun. The plan was made but is never printed.")
    cli.idle(4)

    panel.feature("F04", "Story Turn", "Hybrid",
                  "do, say and story are the three input modes (Strategy pattern). The agent looks lore up "
                  "through tools before it writes. Warning lines under a turn are the F08 consistency check.")
    panel.step("do: an action for Ines.")
    cli.command("do I wade out at low tide toward the archive doors.", pause=0.5)
    cli.expect(r"\n\[[^\n]*turns[^\n]*\]")
    cli.idle(3)
    panel.step("say: Ines speaks.")
    cli.command("say Bram, keep the rope tight.", pause=0.5)
    cli.expect(r"\n\[[^\n]*turns[^\n]*\]")
    cli.idle(3)
    panel.step("story: the user writes a passage and the storyteller continues it.")
    cli.command("story The door gave way, and black water rushed out instead of in.", pause=0.5)
    cli.expect(r"\n\[[^\n]*turns[^\n]*\]")
    cli.idle(4)

    panel.feature("F07", "Lore Proposals", "Hybrid",
                  "The agent proposes lasting new facts through a tool (AI); only approve adds them to the lore.")
    cli.command("proposals", pause=0.5)
    cli.expect(r"No lore proposals waiting\.|Type \"approve <id>\"")
    cli.idle(2)
    ids = re.findall(r"^(P\d+) ", cli.buf[cli.mark - 3000:], re.M)
    if not ids:
        panel.step("Nothing proposed yet: a turn that names something new.")
        cli.command("story Inside, Ines found a reading room lit by a lamp that burned without air.", pause=0.5)
        cli.expect(r"\n\[[^\n]*turns[^\n]*\]")
        cli.idle(2)
        before = len(cli.buf)
        cli.command("proposals", pause=0.5)
        cli.expect(r"No lore proposals waiting\.|Type \"approve <id>\"")
        ids = re.findall(r"^(P\d+) ", cli.buf[before:], re.M)
    if ids:
        panel.step("approve %s: it becomes a lore entry." % ids[0])
        cli.command("approve " + ids[0], pause=2)
        if len(ids) > 1:
            panel.step("reject %s: it never reaches the lore." % ids[1])
            cli.command("reject " + ids[1], pause=2)
        cli.command("lore list", pause=3)
        panel.step("Error path: approving the same proposal twice.")
        cli.command("approve " + ids[0], pause=3.5)
    else:
        panel.step("The agent proposed nothing in these turns.")
        cli.idle(3)

    panel.feature("F10", "Undo, Retry, Edit Turn", "Hybrid",
                  "Command pattern. undo and edit are deterministic; retry calls Claude again.")
    panel.step("undo, then turns: the last turn is gone.")
    cli.command("undo", pause=1.5)
    cli.command("turns", pause=4)
    panel.step("retry: the latest turn is rewritten from the same input.")
    cli.command("retry", pause=0.5)
    cli.expect(r"\n\[[^\n]*turns[^\n]*\]")
    cli.idle(3)
    panel.step("edit: type a replacement, end with a line holding only a dot.")
    cli.command("edit", pause=0.3)
    cli.expect(r"Finish with a line containing only a dot\.")
    cli.idle(1.5)
    cli.answer(r"\| $", "Ines tied the rope off and waited for the water to settle.")
    cli.answer(r"\| $", ".", pause=2.5)
    panel.step("undo restores the original narration.")
    cli.command("undo", pause=1.5)
    cli.command("turns", pause=4)
    panel.step("Error path: an empty edit is rejected.")
    cli.command("edit", pause=0.3)
    cli.answer(r"\| $", ".", pause=3.5)

    panel.feature("F12", "Ending Generation", "AI",
                  "end asks Claude for an ending at any time; with turning points still pending it asks first.")
    cli.command("end", pause=0.3)
    m = cli.expect(r"\(y/n\): $|writing the ending")
    if "y/n" in m.group(0):
        panel.step("Events are still pending, so it asks first. Answer y.")
        cli.idle(2.5)
        cli.type("y")
    cli.expect(r"THE END")
    cli.idle(2)
    panel.step("The ending closes with THE END; the status line says ended. A new turn is refused.")
    cli.command("do look around", pause=3.5)
    cli.command("save", pause=2)
    cli.command("quit", pause=0.5)
    cli.wait_exit()

    panel.feature("F05", "Pivotal Events: no key", "Hybrid",
                  "Error path: the CLI relaunched with ANTHROPIC_API_KEY set to an empty string in the shell. "
                  "Planning fails, so no story starts.")
    sys.stdout.write("\n\033[1m$ ANTHROPIC_API_KEY= java ca.yorku.storyteller.ui.StoryCLI    (key blanked in the shell)\033[0m\n")
    sys.stdout.flush()
    time.sleep(1.5)
    cli = Cli(cp, "")
    cli.expect(r"Type \"help\" for commands\.")
    cli.command("new", pause=1)
    cli.command("start", pause=0.3)
    cli.answer(r"Story idea[^\n]*: $", "")
    cli.answer(r"Title \(optional\): $", "Test")
    cli.answer(r"Premise: $", "A test story")
    cli.answer(r"Tone \(optional\): $", "")
    cli.answer(r"Setting: $", "Ashfall")
    cli.answer(r"Opening situation \(optional\): $", "")
    cli.answer(r"Plot basis[^\n]*: $", "", pause=0.5)
    cli.expect(r"Error:")
    panel.step("No key: planning fails and no story starts.")
    cli.idle(3)
    cli.command("do look around", pause=3.5)

    panel.feature("F09", "Story Memory: no key", "AI",
                  "Error path: without the model the recap cannot be written, but the story still loads.")
    cli.command("load sample-ashfall", pause=0.5)
    cli.expect(r"\n> ", 120)
    cli.mark -= 3
    cli.idle(5)
    cli.command("quit", pause=0.5)
    cli.wait_exit()
    panel.feature("", "End of video", "", "")
    time.sleep(2.5)
    panel.close()


def inner(cp, log):
    status = 0
    try:
        script(cp, log)
    except Exception as e:  # keep the error visible in the recording log
        status = 1
        with open(log + ".error", "w") as f:
            f.write(repr(e))
    with open(log + ".status", "w") as f:
        f.write(str(status))


if __name__ == "__main__":
    if sys.argv[1] == "inner":
        inner(sys.argv[2], sys.argv[3])
    else:
        sys.exit(outer(sys.argv[1], sys.argv[2]))
