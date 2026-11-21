import { useEffect, type ReactNode } from "react";
import { kcSanitize } from "keycloakify/lib/kcSanitize";
import { Banner } from "@/shared/ui/banner";
import type { KcContext } from "./KcContext";
import type { I18n } from "./i18n";
import "../index.css";
import "./auth.css";

export function ServerMessage({ kcContext }: { kcContext: KcContext }) {
  const { message } = kcContext;
  if (
    !message ||
    (message.type === "warning" &&
      kcContext.isAppInitiatedAction &&
      kcContext.pageId !== "info.ftl")
  )
    return null;
  return (
    <Banner
      variant={message.type === "error" ? "danger" : message.type}
      role={message.type === "error" ? "alert" : "status"}
    >
      <span dangerouslySetInnerHTML={{ __html: kcSanitize(message.summary) }} />
    </Banner>
  );
}

export function AuthLayout({
  kcContext,
  i18n,
  title,
  description,
  children,
  displayMessage = true,
}: {
  kcContext: KcContext;
  i18n: I18n;
  title: string;
  description?: string;
  children: ReactNode;
  displayMessage?: boolean;
}) {
  const { msgStr } = i18n;
  const registration = kcContext.pageId === "register.ftl";
  const recovery = [
    "login-reset-password.ftl",
    "login-update-password.ftl",
  ].includes(kcContext.pageId);
  useEffect(() => {
    document.title = `${title} | AgriContract`;
  }, [title]);
  return (
    <main className="auth-shell">
      <div className="auth-card">
        <aside className="auth-brand">
          <div>
            <div className="text-[19px] font-medium">AgriContract</div>
            <p className="mt-1 text-[13px] text-[#BBF7D0]">
              {msgStr("brandTagline")}
            </p>
          </div>
          <div className="auth-brand-story">
            <h2 className="text-[26px] leading-[1.35] font-medium text-white">
              {msgStr(
                registration
                  ? "registerBrandTitle"
                  : recovery
                    ? "resetBrandTitle"
                    : "brandTitle",
              )}
            </h2>
            <p className="mt-4 text-sm leading-relaxed text-[#DCFCE7]">
              {msgStr(
                registration
                  ? "registerBrandDescription"
                  : recovery
                    ? "resetBrandDescription"
                    : "brandDescription",
              )}
            </p>
          </div>
        </aside>
        <section className="auth-content" aria-labelledby="auth-title">
          <h1 id="auth-title" className="mb-1 text-[22px] font-medium">
            {title}
          </h1>
          {description && (
            <p className="mb-7 text-sm leading-relaxed text-text-secondary">
              {description}
            </p>
          )}
          <div className="flex flex-col gap-5">
            {displayMessage && <ServerMessage kcContext={kcContext} />}
            {children}
          </div>
        </section>
      </div>
    </main>
  );
}
