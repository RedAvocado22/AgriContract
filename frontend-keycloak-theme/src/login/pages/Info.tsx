import { AuthLayout } from "../AuthLayout";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";

export default function Info({
  kcContext,
  i18n,
}: {
  kcContext: Extract<KcContext, { pageId: "info.ftl" }>;
  i18n: I18n;
}) {
  const { msgStr, advancedMsg } = i18n;
  const { skipLink, pageRedirectUri, actionUri, client, requiredActions } =
    kcContext;
  const href = pageRedirectUri || actionUri || client.baseUrl;
  return (
    <AuthLayout
      kcContext={kcContext}
      i18n={i18n}
      title={msgStr("informationTitle")}
    >
      {kcContext.messageHeader && (
        <p className="font-medium">{advancedMsg(kcContext.messageHeader)}</p>
      )}
      {requiredActions?.length ? (
        <ul className="list-inside list-disc text-sm">
          {requiredActions.map((action) => (
            <li key={action}>{advancedMsg(`requiredAction.${action}`)}</li>
          ))}
        </ul>
      ) : null}
      {!skipLink && href && (
        <a className="text-center text-sm" href={href}>
          {msgStr(
            !pageRedirectUri && actionUri
              ? "proceedWithAction"
              : "backToApplication",
          )}
        </a>
      )}
    </AuthLayout>
  );
}
