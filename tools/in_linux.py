"""Runs a command on a Linux x64 test machine: a Docker container with an X server nobody sees (Xvfb) and Mesa's
software OpenGL, so the game and our LWJGL component can be tested on Linux from any host, unseen.

    python tools/in_linux.py [--image alma8|ubuntu2404] [--timeout SECONDS] [--env NAME=VALUE ...] [--workdir DIR]
                             -- java ... (a host command line)

The command is written as for the host: paths under the project (and Gradle's user home, for the classpath) are
translated to the container's, ';'-separated classpaths become ':'-separated, and a leading java executable becomes
the container's Temurin 21. The project is mounted read-only except local/run/, where tests write. Exit codes pass
through; 124 means the time ran out (as tools/private_desktop.py). Images: harness/linux/Dockerfile.<name>, built
on first use and tagged with their files' hash.
"""
import argparse
import hashlib
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
IMAGES = ROOT / "harness" / "linux"
GRADLE_HOME = Path(os.environ.get("GRADLE_USER_HOME") or Path.home() / ".gradle")
MOUNTS = [(ROOT, "/of"), (GRADLE_HOME, "/gh")]


def image(name):
    dockerfile = IMAGES / f"Dockerfile.{name}"
    if not dockerfile.exists():
        sys.exit(f"no {dockerfile}")
    digest = hashlib.sha256(dockerfile.read_bytes() + (IMAGES / "run.sh").read_bytes()).hexdigest()[:12]
    tag = f"oldfaithful-linux-test-{name}:{digest}"
    if subprocess.run(["docker", "image", "inspect", tag], capture_output=True).returncode != 0:
        print(f"building {tag}", file=sys.stderr, flush=True)
        subprocess.run(["docker", "build", "-q", "-t", tag, "-f", str(dockerfile), str(IMAGES)], check=True,
                       stdout=subprocess.DEVNULL)
    return tag


def to_container(text):
    """Host paths inside text, translated. Matches the project and Gradle home in either slash style, any case."""
    for host, inside in MOUNTS:
        variants = {str(host), str(host).replace("\\", "/")}
        for v in variants:
            pattern = re.compile(re.escape(v) + r"((?:[\\/][^;:\s\"'=]*)?)", re.I)
            text = pattern.sub(lambda m: inside + m.group(1).replace("\\", "/"), text)
    return text


def translate(arg):
    if ";" in arg and any(str(h).lower() in arg.lower() or str(h).replace("\\", "/").lower() in arg.lower()
                          for h, _ in MOUNTS):
        return ":".join(to_container(part) for part in arg.split(";"))
    return to_container(arg)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--image", default="alma8")
    parser.add_argument("--timeout", type=int, default=600)
    parser.add_argument("--env", action="append", default=[])
    parser.add_argument("--workdir")
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command:
        sys.exit("no command")
    if re.search(r"(^|[\\/])java(\.exe)?$", command[0], re.I):
        command = ["/opt/jdk21/bin/java"] + command[1:]
    run = ROOT / "local" / "run"
    run.mkdir(parents=True, exist_ok=True)
    docker = ["docker", "run", "--rm", "-i", "--init", "--label", "oldfaithful-test",
              "-v", f"{ROOT}:/of:ro", "-v", f"{run}:/of/local/run", "-e", f"OF_TIMEOUT={args.timeout}"]
    if GRADLE_HOME.exists():
        docker += ["-v", f"{GRADLE_HOME}:/gh:ro"]
    for e in args.env:
        docker += ["-e", translate(e)]
    if args.workdir:
        docker += ["-w", translate(args.workdir)]
    docker.append(image(args.image))
    docker += [translate(c) for c in command]
    env = dict(os.environ, MSYS_NO_PATHCONV="1")
    sys.exit(subprocess.run(docker, env=env).returncode)


if __name__ == "__main__":
    main()
