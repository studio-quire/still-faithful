"""Runs a command on a private Windows desktop of its own, for launching the game in tests on a PC someone is using.

    python tools/private_desktop.py [--timeout SECONDS] -- program args...

A desktop made with CreateDesktop is never switched to, so nothing on it can be seen, take the user's focus or receive
their keyboard and mouse input, whatever the program does: its own windows, Java's AWT frames, crash dialogs, console
windows. The command's output goes to
this process's own stdout and stderr. The command and everything it starts run in a job object that is killed when
this process ends for any reason, including being killed itself, and on the timeout (exit code 124). Otherwise the
exit code is the command's.

Not everything stays on the private desktop: the session has one cursor, and its clip rectangle (ClipCursor) is shared
by every desktop. A game that grabs the mouse there confines the user's cursor (LWJGL 2 did, 2026-11-01; a ClipCursor
probe from a private desktop changed the session's clip, checked the same day). Whether SetCursorPos also reaches the
user's cursor from here is unchecked. So callers
keep the game from grabbing (the harness's HandsOff), and this script guards the user's cursor while the command
runs: if the clip rectangle changes, it stops the command at once, puts the clip back (exit code 125); if the cursor
moves three times within two seconds with no mouse input in the 100 ms before (a SetCursorPos while the user's
mouse is still), it stops it too (exit code 126).
With PRIVATE_DESKTOP_NO_GUARD=1 the guard is off (the user's call, 2026-11-03: their own game grabbing the cursor
stopped tests; the harness's HandsOff still keeps the test game from grabbing).

Windows only. Claude Opus 5.5, 2026-11-01.
"""
import ctypes
import os
import subprocess
import sys
import time
from ctypes import wintypes

kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
user32 = ctypes.WinDLL("user32", use_last_error=True)

GENERIC_ALL = 0x10000000
CREATE_SUSPENDED = 0x00000004
CREATE_NO_WINDOW = 0x08000000
CREATE_UNICODE_ENVIRONMENT = 0x00000400
STARTF_USESHOWWINDOW = 0x00000001
STARTF_USESTDHANDLES = 0x00000100
SW_HIDE = 0
HANDLE_FLAG_INHERIT = 0x00000001
STD_HANDLES = (-10, -11, -12)
JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x00002000
JobObjectExtendedLimitInformation = 9
WAIT_TIMEOUT = 0x00000102
INFINITE = 0xFFFFFFFF


class STARTUPINFOW(ctypes.Structure):
    _fields_ = [("cb", wintypes.DWORD), ("lpReserved", wintypes.LPWSTR), ("lpDesktop", wintypes.LPWSTR),
                ("lpTitle", wintypes.LPWSTR), ("dwX", wintypes.DWORD), ("dwY", wintypes.DWORD),
                ("dwXSize", wintypes.DWORD), ("dwYSize", wintypes.DWORD), ("dwXCountChars", wintypes.DWORD),
                ("dwYCountChars", wintypes.DWORD), ("dwFillAttribute", wintypes.DWORD), ("dwFlags", wintypes.DWORD),
                ("wShowWindow", wintypes.WORD), ("cbReserved2", wintypes.WORD), ("lpReserved2", ctypes.c_void_p),
                ("hStdInput", wintypes.HANDLE), ("hStdOutput", wintypes.HANDLE), ("hStdError", wintypes.HANDLE)]


class RECT(ctypes.Structure):
    _fields_ = [("left", wintypes.LONG), ("top", wintypes.LONG), ("right", wintypes.LONG), ("bottom", wintypes.LONG)]


class LASTINPUTINFO(ctypes.Structure):
    _fields_ = [("cbSize", wintypes.UINT), ("dwTime", wintypes.DWORD)]


class PROCESS_INFORMATION(ctypes.Structure):
    _fields_ = [("hProcess", wintypes.HANDLE), ("hThread", wintypes.HANDLE), ("dwProcessId", wintypes.DWORD),
                ("dwThreadId", wintypes.DWORD)]


class IO_COUNTERS(ctypes.Structure):
    _fields_ = [(n, ctypes.c_ulonglong) for n in ("ReadOperationCount", "WriteOperationCount", "OtherOperationCount",
                                                 "ReadTransferCount", "WriteTransferCount", "OtherTransferCount")]


class JOBOBJECT_BASIC_LIMIT_INFORMATION(ctypes.Structure):
    _fields_ = [("PerProcessUserTimeLimit", ctypes.c_longlong), ("PerJobUserTimeLimit", ctypes.c_longlong),
                ("LimitFlags", wintypes.DWORD), ("MinimumWorkingSetSize", ctypes.c_size_t),
                ("MaximumWorkingSetSize", ctypes.c_size_t), ("ActiveProcessLimit", wintypes.DWORD),
                ("Affinity", ctypes.c_size_t), ("PriorityClass", wintypes.DWORD), ("SchedulingClass", wintypes.DWORD)]


class JOBOBJECT_EXTENDED_LIMIT_INFORMATION(ctypes.Structure):
    _fields_ = [("BasicLimitInformation", JOBOBJECT_BASIC_LIMIT_INFORMATION), ("IoInfo", IO_COUNTERS),
                ("ProcessMemoryLimit", ctypes.c_size_t), ("JobMemoryLimit", ctypes.c_size_t),
                ("PeakProcessMemoryUsed", ctypes.c_size_t), ("PeakJobMemoryUsed", ctypes.c_size_t)]


user32.CreateDesktopW.restype = wintypes.HANDLE
user32.CreateDesktopW.argtypes = [wintypes.LPCWSTR, wintypes.LPCWSTR, ctypes.c_void_p, wintypes.DWORD,
                                  wintypes.DWORD, ctypes.c_void_p]
user32.CloseDesktop.argtypes = [wintypes.HANDLE]
kernel32.CreateJobObjectW.restype = wintypes.HANDLE
kernel32.CreateJobObjectW.argtypes = [ctypes.c_void_p, wintypes.LPCWSTR]
kernel32.SetInformationJobObject.argtypes = [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD]
kernel32.AssignProcessToJobObject.argtypes = [wintypes.HANDLE, wintypes.HANDLE]
kernel32.TerminateJobObject.argtypes = [wintypes.HANDLE, wintypes.UINT]
kernel32.CreateProcessW.argtypes = [wintypes.LPCWSTR, wintypes.LPWSTR, ctypes.c_void_p, ctypes.c_void_p,
                                    wintypes.BOOL, wintypes.DWORD, ctypes.c_void_p, wintypes.LPCWSTR,
                                    ctypes.POINTER(STARTUPINFOW), ctypes.POINTER(PROCESS_INFORMATION)]
kernel32.GetStdHandle.restype = wintypes.HANDLE
kernel32.GetStdHandle.argtypes = [wintypes.DWORD]
kernel32.SetHandleInformation.argtypes = [wintypes.HANDLE, wintypes.DWORD, wintypes.DWORD]
kernel32.ResumeThread.argtypes = [wintypes.HANDLE]
kernel32.TerminateProcess.argtypes = [wintypes.HANDLE, wintypes.UINT]
kernel32.WaitForSingleObject.restype = wintypes.DWORD
kernel32.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
kernel32.GetExitCodeProcess.argtypes = [wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD)]
kernel32.CloseHandle.argtypes = [wintypes.HANDLE]
kernel32.GetTickCount.restype = wintypes.DWORD
user32.GetClipCursor.argtypes = [ctypes.POINTER(RECT)]
user32.ClipCursor.argtypes = [ctypes.c_void_p]
user32.GetCursorPos.argtypes = [ctypes.POINTER(wintypes.POINT)]
user32.GetLastInputInfo.argtypes = [ctypes.POINTER(LASTINPUTINFO)]
SM_XVIRTUALSCREEN, SM_YVIRTUALSCREEN, SM_CXVIRTUALSCREEN, SM_CYVIRTUALSCREEN = 76, 77, 78, 79
GUARD_SLICE_MS = 10


class CursorGuard:
    """Watches the session's cursor (clip and programmatic moves) from the user's desktop while the command runs."""

    def __init__(self):
        self.clip = self.read_clip()
        self.pos = self.read_pos()
        self.input = self.read_input()
        self.unexplained = []

    @staticmethod
    def read_clip():
        r = RECT()
        return (r.left, r.top, r.right, r.bottom) if user32.GetClipCursor(ctypes.byref(r)) else None

    @staticmethod
    def read_pos():
        p = wintypes.POINT()
        return (p.x, p.y) if user32.GetCursorPos(ctypes.byref(p)) else None

    @staticmethod
    def read_input():
        info = LASTINPUTINFO()
        info.cbSize = ctypes.sizeof(info)
        return info.dwTime if user32.GetLastInputInfo(ctypes.byref(info)) else None

    def restore_clip(self):
        g = user32.GetSystemMetrics
        whole = (g(SM_XVIRTUALSCREEN), g(SM_YVIRTUALSCREEN), g(SM_XVIRTUALSCREEN) + g(SM_CXVIRTUALSCREEN),
                 g(SM_YVIRTUALSCREEN) + g(SM_CYVIRTUALSCREEN))
        if self.clip is None or self.clip == whole:
            user32.ClipCursor(None)
        else:
            user32.ClipCursor(ctypes.byref(RECT(*self.clip)))

    def problem(self):
        """None while the cursor is untouched; otherwise (exit code, what happened)."""
        clip = self.read_clip()
        if clip is not None and self.clip is not None and clip != self.clip:
            return 125, f"the cursor's clip rectangle changed from {self.clip} to {clip}"
        pos, inp = self.read_pos(), self.read_input()
        # GetLastInputInfo ticks at the system timer (about 15 ms), so a move counts as the user's if any input came in
        # the last 100 ms. Warps while the user is moving the mouse go unnoticed; the clip check doesn't depend on it.
        idle_ms = (kernel32.GetTickCount() - inp) & 0xFFFFFFFF if inp is not None else 0
        if pos is not None and self.pos is not None and pos != self.pos and idle_ms > 100:
            now = time.monotonic()
            self.unexplained = [t for t in self.unexplained if now - t < 2] + [now]
            if len(self.unexplained) >= 3:
                return 126, f"the cursor moved with no mouse input behind it (to {pos})"
        self.pos, self.input = pos, inp
        return None


def check(ok, what):
    if not ok:
        raise OSError(ctypes.get_last_error(), f"{what} failed: {ctypes.FormatError(ctypes.get_last_error())}")


def run(command, timeout):
    name = f"OldFaithfulTest-{os.getpid()}"
    desktop = user32.CreateDesktopW(name, None, None, 0, GENERIC_ALL, None)
    check(desktop, "CreateDesktopW")
    job = kernel32.CreateJobObjectW(None, None)
    check(job, "CreateJobObjectW")
    limits = JOBOBJECT_EXTENDED_LIMIT_INFORMATION()
    limits.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
    check(kernel32.SetInformationJobObject(job, JobObjectExtendedLimitInformation, ctypes.byref(limits),
                                           ctypes.sizeof(limits)), "SetInformationJobObject")

    si = STARTUPINFOW()
    si.cb = ctypes.sizeof(si)
    si.lpDesktop = name
    si.dwFlags = STARTF_USESTDHANDLES | STARTF_USESHOWWINDOW
    si.wShowWindow = SW_HIDE
    handles = [kernel32.GetStdHandle(h) for h in STD_HANDLES]
    for h in handles:
        if h and h != wintypes.HANDLE(-1).value:
            kernel32.SetHandleInformation(h, HANDLE_FLAG_INHERIT, HANDLE_FLAG_INHERIT)
    si.hStdInput, si.hStdOutput, si.hStdError = handles
    pi = PROCESS_INFORMATION()
    cmdline = ctypes.create_unicode_buffer(subprocess.list2cmdline(command))
    flags = CREATE_SUSPENDED | CREATE_NO_WINDOW | CREATE_UNICODE_ENVIRONMENT
    check(kernel32.CreateProcessW(None, cmdline, None, None, True, flags, None, None, ctypes.byref(si),
                                  ctypes.byref(pi)), "CreateProcessW")
    try:
        # In the job before it runs a single instruction, so nothing it starts escapes the kill.
        if not kernel32.AssignProcessToJobObject(job, pi.hProcess):
            kernel32.TerminateProcess(pi.hProcess, 1)
            check(False, "AssignProcessToJobObject")
        guard = None if os.environ.get("PRIVATE_DESKTOP_NO_GUARD") == "1" else CursorGuard()
        if guard:
            print(f"[private_desktop] cursor guard on: clip {guard.clip}, checked every {GUARD_SLICE_MS} ms", file=sys.stderr, flush=True)
        else:
            print("[private_desktop] cursor guard off (PRIVATE_DESKTOP_NO_GUARD=1)", file=sys.stderr, flush=True)
        kernel32.ResumeThread(pi.hThread)
        deadline = None if timeout is None else time.monotonic() + timeout
        while kernel32.WaitForSingleObject(pi.hProcess, GUARD_SLICE_MS) == WAIT_TIMEOUT:
            trouble = guard.problem() if guard else None
            if trouble:
                kernel32.TerminateJobObject(job, trouble[0])
                if trouble[0] == 125:
                    guard.restore_clip()
                print(f"[private_desktop] CURSOR GUARD: {trouble[1]}; stopped the command"
                      + ("; clip restored" if trouble[0] == 125 else ""), file=sys.stderr, flush=True)
                kernel32.WaitForSingleObject(pi.hProcess, 10000)
                return trouble[0]
            if deadline is not None and time.monotonic() > deadline:
                print(f"[private_desktop] timed out after {timeout} s; stopping the command", file=sys.stderr, flush=True)
                kernel32.TerminateJobObject(job, 124)
                kernel32.WaitForSingleObject(pi.hProcess, 10000)
                return 124
        code = wintypes.DWORD()
        check(kernel32.GetExitCodeProcess(pi.hProcess, ctypes.byref(code)), "GetExitCodeProcess")
        return code.value
    finally:
        # Anything the command left running goes with the job.
        kernel32.TerminateJobObject(job, 1)
        kernel32.CloseHandle(pi.hThread)
        kernel32.CloseHandle(pi.hProcess)
        kernel32.CloseHandle(job)
        user32.CloseDesktop(desktop)


def main(argv):
    timeout = None
    if "--" not in argv:
        sys.exit(__doc__)
    split = argv.index("--")
    opts, command = argv[:split], argv[split + 1:]
    if opts[:1] == ["--timeout"] and len(opts) == 2:
        timeout = float(opts[1])
    elif opts:
        sys.exit(__doc__)
    if not command:
        sys.exit(__doc__)
    sys.stdout.flush()
    return run(command, timeout)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
