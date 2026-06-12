import json
import pathlib
import re
import sys
import urllib.request

token = sys.argv[1]
repo = sys.argv[2] if len(sys.argv) > 2 else "Ithan-Cassiano/Kosen-Releases"
versions = sys.argv[3:]
notes_root = pathlib.Path(__file__).resolve().parent.parent / "release-notes"
build_gradle = notes_root.parent / "app" / "build.gradle"

RELEASE_VERSION_CODES = {
    "9.7.14": 2052,
    "9.7.15": 2053,
    "9.7.16": 2054,
    "9.7.17": 2055,
    "9.7.18": 2056,
    "9.7.19": 2057,
    "9.7.20": 2058,
    "1.0.0": 10000,
    "1.0.1": 10001,
    "1.0.2": 10002,
}

DEV_VERSION_CODES = {
    "1.0.5": 10005,
    "1.0.6": 10006,
    "1.0.7": 10007,
    "1.0.8": 10008,
    "1.0.9": 10009,
    "1.0.10": 10010,
    "1.0.11": 10011,
}


def notes_dir_for_repo(repo_name: str) -> pathlib.Path:
    if "Dev-Releases" in repo_name:
        return notes_root / "dev"
    return notes_root / "release"


def read_default_version_code(channel: str = "release") -> int:
    if not build_gradle.exists():
        return 0
    text = build_gradle.read_text(encoding="utf-8")
    pattern = r"devVersionCode\s*=\s*(\d+)" if channel == "dev" else r"releaseVersionCode\s*=\s*(\d+)"
    match = re.search(pattern, text)
    return int(match.group(1)) if match else 0


def append_version_code_marker(body: str, version_code: int) -> str:
    if version_code <= 0 or re.search(r"(?:<!--\s*)?versionCode:\d+", body):
        return body
    return body.rstrip() + f"\n\n[versionCode:{version_code}]"


def append_channel_marker(body: str, channel: str) -> str:
    marker = "[channel:dev]" if channel == "dev" else "[channel:release]"
    if re.search(r"\[channel:(dev|release)\]", body):
        return body
    return body.rstrip() + f"\n\n{marker}"


channel = "dev" if "Dev-Releases" in repo else "release"
notes_dir = notes_dir_for_repo(repo)
default_version_code = read_default_version_code(channel)
known_codes = DEV_VERSION_CODES if channel == "dev" else RELEASE_VERSION_CODES

if not versions:
    sys.exit(0)

for arg in versions:
    if ":" in arg:
        ver, version_code = arg.split(":", 1)
        version_code = int(version_code)
    else:
        ver = arg
        version_code = known_codes.get(ver, default_version_code)

    notes_file = notes_dir / f"v{ver}.md"
    if not notes_file.exists():
        raise SystemExit(f"Release notes não encontradas: {notes_file}")

    body = notes_file.read_text(encoding="utf-8")
    body = append_version_code_marker(body, version_code)
    body = append_channel_marker(body, channel)
    req = urllib.request.Request(f"https://api.github.com/repos/{repo}/releases/tags/v{ver}")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req) as resp:
        release = json.load(resp)
    payload = json.dumps({"body": body}, ensure_ascii=False).encode("utf-8")
    patch = urllib.request.Request(
        f"https://api.github.com/repos/{repo}/releases/{release['id']}",
        data=payload,
        method="PATCH",
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json; charset=utf-8",
        },
    )
    with urllib.request.urlopen(patch):
        pass
    print(f"updated v{ver} on {repo}: {body.splitlines()[0]} (versionCode={version_code})")
