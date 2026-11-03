"""How far mouse look turns for the same hand movement: LWJGL 2 on Windows versus our layer (docs/lwjgl.md > Mouse).

    python tools/mouse_ballistics.py [--dpi 800] [--rate 1000]

LWJGL 2 on Windows read grabbed-mouse motion from the desktop pointer (WM_MOUSEMOVE positions, re-centred each
frame), so the game saw the pointer's movement: the device's counts ("mickeys") after Windows' pointer speed slider
and, with "Enhance pointer precision" on (Windows' default), its acceleration curve. Our layer reads raw input
through SDL; SDL_HINT_MOUSE_RELATIVE_SYSTEM_SCALE applies SDL's port of Windows' ballistics to it, per input report,
from the same registry curves and settings. This script reads this PC's settings, feeds a synthetic mouse (a given
DPI and report rate, moving at steady hand speeds) through that port, and prints how many pixels the game sees per
count, which is what the game turns by. Raw input is 1.0 by definition.

The port follows SDL 3.4.18's WIN_ApplySystemScale (src/video/windows/SDL_windowsmouse.c) in its fixed-point
arithmetic. It is a model of Windows, not Windows itself: checking it against the real pointer means injecting
input (SendInput), which moves the cursor, so only on a PC nobody is using.

Claude Opus 5.5, 2026-11-01.
"""
import argparse
import ctypes
import sys
import winreg

SPI_GETMOUSE = 0x0003
SPI_GETMOUSESPEED = 0x0070
MASK64 = (1 << 64) - 1


def system_settings():
    user32 = ctypes.windll.user32
    speed = ctypes.c_int(10)
    user32.SystemParametersInfoW(SPI_GETMOUSESPEED, 0, ctypes.byref(speed), 0)
    params = (ctypes.c_int * 3)()
    user32.SystemParametersInfoW(SPI_GETMOUSE, 0, params, 0)
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Control Panel\Mouse") as key:
        xcurve = winreg.QueryValueEx(key, "SmoothMouseXCurve")[0]
        ycurve = winreg.QueryValueEx(key, "SmoothMouseYCurve")[0]
    low_dwords = lambda b: [int.from_bytes(b[i * 8:i * 8 + 4], "little") for i in range(5)]
    return max(1, min(speed.value, 20)), bool(params[2]), low_dwords(xcurve), low_dwords(ycurve)


class WindowsBallistics:
    """SDL's WIN_ApplySystemScale for a per-monitor-aware window at 100% scaling, Windows 8 or later."""

    def __init__(self, slider, enhanced, xcurve, ycurve, dpi=96):
        self.enhanced = enhanced
        self.dpi = dpi
        self.dpiscale = max(slider, (slider - 2) * 4, (slider - 6) * 8)
        self.denom = (10 * 120) << 16
        self.xs = [0] + [7 * xcurve[i] for i in range(1, 5)]
        self.ys = [0] + [(slider * ycurve[i]) << 17 for i in range(1, 5)]
        self.last_node = 0

    def apply(self, x, y):
        ix, iy = int(x) * 65536, int(y) * 65536
        if not self.enhanced:
            f = lambda v: c_div(c_div(v * self.dpiscale * self.dpi, 96), 32)
            return f(ix) / 65536, f(iy) / 65536
        ax, ay = abs(ix), abs(iy)
        speed = min(ax, ay) + (max(ax, ay) << 1)
        if speed == 0:
            return 0.0, 0.0
        i = 1
        while i < 5:
            j = i
            if speed < self.xs[j]:
                break
            i += 1
        i -= 1
        j -= 1
        k, self.last_node = self.last_node, j
        denom = self.denom
        scale = self.segment(j, i, speed)
        if j > k:
            denom <<= 1
            scale += self.segment(k, k, speed)
        scale *= self.dpi
        return c_div(ix * scale, denom) / 65536, c_div(iy * scale, denom) / 65536

    def segment(self, j, i, speed):
        xdiff, ydiff = self.xs[j + 1] - self.xs[j], self.ys[j + 1] - self.ys[j]
        if xdiff == 0:
            return 0
        slope = c_div(ydiff, xdiff)
        inter = to_signed((slope * self.xs[i] - self.ys[i]) & MASK64)
        # C divides a signed 64-bit value by an unsigned one as unsigned.
        return to_signed((slope - ((inter & MASK64) // speed)) & MASK64)


def c_div(a, b):
    q = abs(a) // abs(b)
    return q if (a >= 0) == (b >= 0) else -q


def to_signed(v):
    return v - (1 << 64) if v >= 1 << 63 else v


def gain(ballistics, counts_per_report, reports=2000):
    """Pixels per count for a mouse moving steadily at counts_per_report (fractions carried, as a sensor does)."""
    carried = 0.0
    counts = pixels = 0.0
    for _ in range(reports):
        carried += counts_per_report
        n = int(carried)
        carried -= n
        if n:
            counts += n
            pixels += ballistics.apply(n, 0)[0]
    return pixels / counts if counts else 0.0


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--dpi", type=int, default=800, help="the mouse's counts per inch")
    parser.add_argument("--rate", type=int, default=1000, help="reports per second")
    a = parser.parse_args()
    slider, enhanced, xcurve, ycurve = system_settings()
    print(f"This PC: pointer speed {slider}/20, Enhance pointer precision {'on' if enhanced else 'off'}")
    print(f"Mouse: {a.dpi} DPI, {a.rate} Hz\n")
    print(f"{'hand speed':>12} {'counts/report':>14} {'LWJGL 2 (Windows pointer)':>26} {'ours, raw':>10}")
    for cm_per_s in (1, 2, 5, 10, 20, 40, 80):
        per_report = cm_per_s / 2.54 * a.dpi / a.rate
        g = gain(WindowsBallistics(slider, enhanced, xcurve, ycurve), per_report)
        print(f"{cm_per_s:>8} cm/s {per_report:>14.2f} {g:>26.3f} {1.0:>10.3f}")
    print("\nWith the system scale on (our default now), ours follows the LWJGL 2 column.")


if __name__ == "__main__":
    sys.exit(main())
