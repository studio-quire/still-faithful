#!/bin/sh
# Entry point of the Linux test images: an X server on :99 that nobody sees, then the command under a time limit
# (exit 124 when it runs out, as tools/private_desktop.py does on Windows). OF_TIMEOUT is in seconds.
Xvfb :99 -screen 0 1920x1080x24 -nolisten tcp -noreset >/tmp/xvfb.log 2>&1 &
for i in $(seq 50); do [ -e /tmp/.X11-unix/X99 ] && break; sleep 0.1; done
[ -e /tmp/.X11-unix/X99 ] || { echo "Xvfb didn't start:"; cat /tmp/xvfb.log; exit 125; }
export DISPLAY=:99
exec timeout --kill-after=10 "${OF_TIMEOUT:-600}" "$@"
