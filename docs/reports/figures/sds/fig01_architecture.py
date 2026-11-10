"""SDS Figure 1: overall architecture (target Phase 2) as a UML component diagram in Visual Paradigm notation.

Tiers are packages; dashed arrows are dependencies (who calls whom), labelled with the protocol. An arrow that ends
on a tier frame means every service of that tier (REST from the gateway) or that starts on it means every service
uses that store. Object storage is SeaweedFS (S3 API), which replaced MinIO (decision of 10/2026).
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from vpdiagram import Diagram  # noqa: E402

OUT = Path(__file__).resolve().parent


def build():
    d = Diagram("fig01_architecture", 800, 760)
    d.package("client", 20, 30, 540, 80, "Client Tier")
    d.component("spa", 290, 70, "React SPA")
    d.component("verifier", 680, 70, "External Verifier", stereotype="external", w=170)

    d.package("edge", 20, 160, 530, 90, "Edge Tier")
    d.component("keycloak", 120, 205, "Keycloak 24")
    d.component("nginx", 290, 205, "Nginx")
    d.component("gateway", 455, 205, "API Gateway")

    d.package("app", 20, 300, 775, 250, "Application Tier")
    d.component("contract", 130, 370, "contract-service")
    d.component("others", 340, 370, "escrow, inspection,\nreputation, product,\nfile, pricing, analytics",
                stereotype="subsystem")
    d.component("bank", 555, 370, "bank-service")
    d.component("audit", 718, 370, "audit-service")
    d.component("user", 100, 490, "user-service")
    d.component("notif", 345, 490, "notification-service")

    d.package("data", 20, 650, 620, 90, "Data & Messaging Tier")
    d.component("stores", 140, 695, "Redis 7 · SeaweedFS\n(S3) · ClamAV")
    d.component("mq", 345, 695, "RabbitMQ 3.13")
    d.component("mysql", 535, 695, "MySQL 8\n(schema per service)")
    d.component("smtp", 722, 695, "Mail provider", stereotype="external", w=140)

    d.dep("spa", "l", "keycloak", "t", (120, 70), label="OIDC", seg=1)
    d.dep("spa", "b", "nginx", "t", label="HTTPS", side="r")
    d.dep("nginx", "r", "gateway", "l", label="forward", side="t")
    d.dep("gateway", "b", "app", "t", label="REST /api/v1 (JWT)", side="l")
    d.dep("verifier", "b", "bank", "t", (680, 130), (555, 130), label="emergency lock", seg=1, side="t")
    d.dep("verifier", "b", "audit", "t", s_off=38, label="audit hash", side="r")
    d.dep("contract", "b", "user", "t", s_off=-30, label="internal REST")
    d.dep("contract", "b", "notif", "l", (190, 490), s_off=60, label="OTP e-mail", seg=1, side="t")
    d.dep("notif", "r", "smtp", "t", (722, 490), label="SMTP", side="t")
    d.dep("app", "b", "stores", "t", s_off=210 - 407.5, d_off=70, label="cache · S3 · clamd", side="r")
    d.dep("app", "b", "mq", "t", s_off=345 - 407.5, label="AMQP", side="r")
    d.dep("app", "b", "mysql", "t", s_off=535 - 407.5, label="JDBC", side="r")
    return d


if __name__ == "__main__":
    build().write(OUT)
    print("ok")
