"""SDS Figure 2: backend package diagram, contract-service as the reference, from the code.

Packages are those under com.agricontract.contract (checked 10/11/2026); an arrow is a dependency found in the
import statements of the main code. Only the main dependencies are drawn; every one of them points inwards
(infrastructure -> application -> domain), none the other way.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from vpdiagram import Diagram  # noqa: E402

OUT = Path(__file__).resolve().parent


def build():
    d = Diagram("fig02_backend_packages", 800, 620)
    d.package("infra", 10, 40, 230, 440, "infrastructure")
    for i, name in enumerate(["persistence", "terms", "consent", "web", "messaging", "feign", "notification", "otp",
                              "config"]):
        y = [50, 94, 138, 200, 244, 300, 344, 388, 432][i]
        d.leaf(name, 30, y, name, w=190, h=32)
    d.package("app", 270, 190, 260, 290, "application")
    d.leaf("usecase", 300, 205, "usecase", w=200, h=80)
    d.leaf("port", 300, 300, "port", w=200, h=115)
    d.leaf("service", 290, 430, "service", h=32)
    d.leaf("dto", 372, 430, "dto", h=32)
    d.leaf("app_exception", 432, 430, "exception", h=32)
    d.package("domain", 560, 40, 200, 440, "domain")
    d.leaf("model", 590, 90, "model", w=140, h=160)
    d.leaf("repository", 590, 265, "repository", w=140, h=32)
    d.leaf("event", 590, 330, "event", w=140, h=32)
    d.leaf("dom_exception", 590, 380, "exception", w=140, h=32)
    d.leaf("eventing", 40, 545, "com.agricontract.eventing", h=38)
    d.leaf("calendar", 236, 545, "com.agricontract.calendar", h=38)

    d.dep("persistence", "t", "repository", "r", (185, 12), (780, 12), (780, 281), s_off=60)
    d.dep("terms", "r", "model", "l", d_off=110 - 170)
    d.dep("consent", "r", "model", "l", d_off=154 - 170)
    d.dep("web", "r", "usecase", "l", d_off=216 - 245)
    d.dep("messaging", "r", "usecase", "l", d_off=260 - 245)
    d.dep("feign", "r", "port", "l", d_off=316 - 357.5)
    d.dep("notification", "r", "port", "l", d_off=360 - 357.5)
    d.dep("otp", "r", "port", "l", d_off=404 - 357.5)
    d.dep("usecase", "b", "port", "t")
    d.dep("usecase", "r", "model", "l")
    d.dep("usecase", "r", "repository", "l")
    d.dep("messaging", "l", "eventing", "l", (18, 260), (18, 564))
    d.dep("config", "r", "calendar", "t", (250, 448))
    d.legend("legend", 10, 597, "Dashed arrow: package dependency found in the imports (main ones only); every "
                                "dependency points inwards: infrastructure → application → domain")
    return d


if __name__ == "__main__":
    build().write(OUT)
    print("ok")
