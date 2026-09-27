#!/usr/bin/env python3
"""Drives the installed app on a running emulator with adb, for the screen recording.

Usage: python3 scripts/demo.py OUT_DIR
Needs adb on PATH and one emulator attached. Screenshots are written to OUT_DIR.
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta

PKG = "com.darshpandya.taskmaster"
OUT = sys.argv[1] if len(sys.argv) > 1 else "."


def adb(*args, check=True):
    return subprocess.run(["adb", *args], check=check, capture_output=True, text=True).stdout


def sh(cmd):
    return adb("shell", cmd)


def dump():
    for _ in range(5):
        xml = adb("exec-out", "uiautomator", "dump", "/dev/tty", check=False)
        start = xml.find("<?xml")
        end = xml.rfind("</hierarchy>")
        if start >= 0 and end > 0:
            return ET.fromstring(xml[start:end + len("</hierarchy>")])
        time.sleep(0.5)
    raise RuntimeError("uiautomator dump failed")


def settle(timeout=6):
    """Waits until two consecutive dumps show the same layout (list animations finished)."""
    prev = None
    deadline = time.time() + timeout
    while time.time() < deadline:
        cur = [(n.get("text"), n.get("bounds")) for n in dump().iter("node")]
        if cur == prev:
            return
        prev = cur
        time.sleep(0.3)


def find(text=None, rid=None, desc=None, contains=None, itext=None, timeout=10):
    deadline = time.time() + timeout
    while time.time() < deadline:
        for n in dump().iter("node"):
            if text is not None and n.get("text") != text:
                continue
            if itext is not None and (n.get("text") or "").lower() != itext.lower():
                continue
            if contains is not None and contains not in (n.get("text") or ""):
                continue
            if rid is not None and not (n.get("resource-id") or "").endswith(rid):
                continue
            if desc is not None and n.get("content-desc") != desc:
                continue
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
            return (x1 + x2) // 2, (y1 + y2) // 2, (x1, y1, x2, y2)
        time.sleep(0.4)
    raise RuntimeError(f"not found: text={text} itext={itext} rid={rid} desc={desc} contains={contains}")


def tap(pause=0.9, wait=True, **kw):
    if wait:
        settle()
    x, y, _ = find(**kw)
    sh(f"input tap {x} {y}")
    time.sleep(pause)


def type_text(s, pause=0.6):
    for i, word in enumerate(s.split(" ")):
        if i:
            sh("input keyevent 62")
        if word:
            sh("input text " + word.replace("'", "\\'").replace(",", "\\,").replace("&", "\\&"))
    time.sleep(pause)


def hide_keyboard():
    if "mInputShown=true" in sh("dumpsys input_method"):
        sh("input keyevent 111")  # escape closes the IME without leaving the screen
        time.sleep(0.5)


def shot(name):
    with open(f"{OUT}/{name}.png", "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], check=True, capture_output=True).stdout)


def new_task(title, notes="", priority=None, due=None):
    tap(rid="id/fab")
    tap(rid="id/title_input")
    type_text(title)
    if notes:
        tap(rid="id/notes_input")
        type_text(notes)
    hide_keyboard()
    if priority:
        tap(text=priority)
    if due:
        set_due(due)
    tap(rid="id/save", pause=1.5)


def set_due(when):
    tap(rid="id/due_button")
    tap(text="OK")  # date picker opens on today's date
    tap(rid="android:id/toggle_mode", pause=0.8)
    tap(rid="android:id/input_hour", pause=0.3)
    sh("input keyevent KEYCODE_MOVE_END")
    for _ in range(2):
        sh("input keyevent KEYCODE_DEL")
    type_text(f"{when.hour:02d}", pause=0.3)
    tap(rid="android:id/input_minute", pause=0.3)
    sh("input keyevent KEYCODE_MOVE_END")
    for _ in range(2):
        sh("input keyevent KEYCODE_DEL")
    type_text(f"{when.minute:02d}", pause=0.5)
    tap(text="OK", pause=1.0)


def open_task(title):
    tap(text=title)
    find(rid="id/title_input", text=title)  # loaded
    time.sleep(0.8)


def delete_open_task():
    tap(desc="Delete", pause=1.2)
    x, y, _ = find(rid="android:id/button1", text="Delete")
    sh(f"input tap {x} {y}")
    time.sleep(1.5)


def device_now():
    return datetime.fromtimestamp(int(sh("date +%s").strip()))


def overflow(item):
    for attempt in range(3):
        time.sleep(1.0)
        tap(desc="More options")
        try:
            x, y, _ = find(text=item, timeout=3)
        except RuntimeError:
            sh("input keyevent 111")
            continue
        sh(f"input tap {x} {y}")
        time.sleep(1.2)
        return
    raise RuntimeError(f"menu item {item} not found")


def main():
    sh(f"am start -W -n {PKG}/.ui.MainActivity")
    time.sleep(2)
    shot("01-empty")

    new_task("Buy groceries", "milk, eggs, bread", "High")
    now = device_now()
    due = (now + timedelta(minutes=5)).replace(second=0, microsecond=0)
    new_task("Submit expense report", "September receipts", "Medium", due=due)
    new_task("Call the dentist", "", "Low")
    new_task("Renew library books")
    shot("02-list")

    # Edit: open the groceries task and change its title.
    open_task("Buy groceries")
    _, y, (x1, y1, x2, y2) = find(rid="id/title_input")
    sh(f"input tap {x2 - 30} {y}")  # put the cursor at the end of the text
    time.sleep(0.5)
    type_text(" for the week")
    hide_keyboard()
    shot("03-edit")
    tap(rid="id/save", pause=1.5)

    # Complete: tick the dentist task, then look at the Done and Active filters.
    settle()
    cx, cy, _ = find(desc='Mark "Call the dentist" done')
    sh(f"input tap {cx} {cy}")
    time.sleep(1.5)
    shot("04-completed")
    tap(text="Done", pause=1.5)
    tap(text="Active", pause=1.5)
    tap(text="All", pause=1.2)

    # Search.
    tap(desc="Search")
    type_text("report", pause=1.5)
    shot("05-search")
    hide_keyboard()
    tap(desc="Collapse", pause=1.2)

    # Delete: swipe one task away, undo, then delete it from its edit screen.
    settle()
    x, y, (x1, y1, x2, y2) = find(text="Renew library books")
    sh(f"input swipe {x2 - 20} {y} {x1 + 20} {y} 250")
    time.sleep(0.8)
    shot("06-swipe-undo")
    tap(text="Undo", pause=1.5, wait=False)
    open_task("Renew library books")
    delete_open_task()

    # Export to a file with the system document picker.
    overflow("Export backup…")
    time.sleep(2)
    shot("07-export-picker")
    tap(itext="save", pause=2.0)
    time.sleep(1.5)
    shot("08-exported")

    # Delete a task, then import the backup to bring it back.
    open_task("Buy groceries for the week")
    delete_open_task()
    overflow("Import backup…")
    tap(text="Choose file", pause=2.5)
    x, y, _ = find(contains="taskmaster-backup-", timeout=15)
    sh(f"input tap {x} {y}")
    time.sleep(2.5)
    shot("09-imported")

    # Drive is present in the menu but not configured.
    overflow("Google Drive backup (not configured)")
    shot("10-drive-not-configured")
    tap(text="OK")

    # Wait for the reminder on the expense report task.
    print("waiting for reminder due at", due.strftime("%H:%M"), flush=True)
    deadline = time.time() + 300
    while time.time() < deadline:
        if "Submit expense report" in sh("dumpsys notification --noredact | grep -i 'android.title'"):
            break
        time.sleep(2)
    else:
        raise RuntimeError("reminder did not fire")
    print("reminder posted at", datetime.now().strftime("%H:%M:%S"), flush=True)
    time.sleep(1.5)
    sh("cmd statusbar expand-notifications")
    time.sleep(2.5)
    shot("11-reminder")
    tap(text="Mark done", pause=2.0)
    sh("cmd statusbar collapse")
    time.sleep(2)
    shot("12-after-mark-done")


if __name__ == "__main__":
    main()
