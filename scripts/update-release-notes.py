import json
import pathlib
import sys
import urllib.request

token = sys.argv[1]
repo = "Ithan-Cassiano/Kosen-Releases"
notes_dir = pathlib.Path(__file__).resolve().parent.parent / "release-notes"

for ver in sys.argv[2:]:
    body = (notes_dir / f"v{ver}.md").read_text(encoding="utf-8")
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
    print(f"updated v{ver}: {body.splitlines()[0]}")
