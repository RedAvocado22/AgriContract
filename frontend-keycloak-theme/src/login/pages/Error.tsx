import { AuthLayout } from "../AuthLayout";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";

export default function Error({
  kcContext,
  i18n,
}: {
  kcContext: Extract<KcContext, { pageId: "error.ftl" }>;
  i18n: I18n;
}) {
  return (
    <AuthLayout
      kcContext={kcContext}
      i18n={i18n}
      title={i18n.msgStr("errorTitle")}
    >
      {!kcContext.skipLink && kcContext.client?.baseUrl && (
        <a className="text-center text-sm" href={kcContext.client.baseUrl}>
          {i18n.msgStr("backToApplication")}
        </a>
      )}
    </AuthLayout>
  );
}
