"""Smoke test: start the compiled game under a pty, enter a name, press Q, check it exits.

Usage: python3 smoke_test.py build/snake
"""
import os
import pty
import select
import sys
import time

binary = sys.argv[1]
pid, fd = pty.fork()
if pid == 0:
    os.execv(binary, [binary])

out = b""


def pump(seconds):
    global out
    end = time.time() + seconds
    while time.time() < end:
        r, _, _ = select.select([fd], [], [], 0.05)
        if r:
            try:
                data = os.read(fd, 4096)
            except OSError:
                return
            if not data:
                return
            out += data


pump(1.0)
os.write(fd, b"tester\n")
pump(1.0)
os.write(fd, b"q")
pump(2.0)

deadline = time.time() + 5
status = None
while time.time() < deadline:
    done, st = os.waitpid(pid, os.WNOHANG)
    if done:
        status = st
        break
    pump(0.1)
if status is None:
    os.kill(pid, 9)
    sys.exit("FAIL: game did not exit after Q")

text = out.decode(errors="replace")
if not (os.WIFEXITED(status) and os.WEXITSTATUS(status) == 0):
    sys.exit("FAIL: non-zero exit status %r" % status)
if "tester's Score: 0" not in text:
    sys.exit("FAIL: score line not found in output")
print("OK: game started, showed score line for 'tester', and exited cleanly on Q")
