"""SDS Figure 3: frontend package diagram (frontend/src), from the folders that exist on 10/11/2026.

auth is drawn expanded as the reference feature; the other features have the same api / hooks / components folders.
Arrows: routes -> feature components, components -> lib / shared ui, shared ui -> shared lib and routes -> shared ui
are found in the imports; components -> hooks -> api -> shared api is the convention the features follow as their
API calls are added.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from vpdiagram import Diagram  # noqa: E402

OUT = Path(__file__).resolve().parent


def build():
    d = Diagram("fig03_frontend_packages", 800, 520)
    d.package("src", 10, 40, 780, 450, "frontend/src")
    d.leaf("routes", 30, 90, "routes", w=120, h=330)
    d.package("features", 180, 90, 340, 380, "features")
    d.package("auth", 200, 120, 300, 230, "auth")
    d.leaf("components", 220, 140, "components", w=120, h=40)
    d.leaf("lib", 370, 140, "lib", w=110, h=40)
    d.leaf("hooks", 220, 210, "hooks", w=120, h=40)
    d.leaf("api", 220, 280, "api", w=120, h=40)
    d.leaf("contracts", 200, 370, "contracts", w=140, h=36)
    d.leaf("escrow", 360, 370, "escrow", w=140, h=36)
    d.leaf("listings", 200, 420, "listings", w=140, h=36)
    d.leaf("notifications", 360, 420, "notifications", w=140, h=36)
    d.package("shared", 550, 90, 220, 380, "shared")
    d.leaf("ui", 570, 108, "ui", w=180, h=62)
    d.leaf("slib", 570, 210, "lib", w=180, h=40)
    d.leaf("sapi", 570, 280, "api", w=180, h=40)
    d.leaf("shooks", 570, 350, "hooks", w=180, h=40)

    d.dep("routes", "r", "components", "l", s_off=164 - 255, d_off=4)
    d.dep("routes", "t", "ui", "t", (90, 50), (660, 50))
    d.dep("components", "r", "lib", "l", d_off=4, s_off=4)
    d.dep("components", "t", "ui", "l", (300, 128), s_off=20, d_off=128 - 139)
    d.dep("components", "b", "hooks", "t")
    d.dep("hooks", "b", "api", "t")
    d.dep("api", "r", "sapi", "l")
    d.dep("ui", "b", "slib", "t")
    d.legend("legend", 10, 498, "auth expanded as the reference feature; contracts, escrow, listings and "
                                "notifications have the same api / hooks / components folders")
    return d


if __name__ == "__main__":
    build().write(OUT)
    print("ok")
