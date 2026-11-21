import { Button } from "@/shared/ui/button";
import { AuthLayout } from "../AuthLayout";
import { useKeycloakForm, TextInput, z } from "../form";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";

export default function ResetPassword({
  kcContext,
  i18n,
}: {
  kcContext: Extract<KcContext, { pageId: "login-reset-password.ftl" }>;
  i18n: I18n;
}) {
  const { msgStr } = i18n;
  const { url, auth, realm } = kcContext;
  const form = useKeycloakForm(
    z.object({ username: z.string().min(1, msgStr("requiredField")) }),
    { username: auth.attemptedUsername ?? "" },
  );
  return (
    <AuthLayout
      kcContext={kcContext}
      i18n={i18n}
      title={msgStr("emailForgotTitle")}
      description={msgStr("resetDescription")}
    >
      <form
        id="kc-reset-password-form"
        action={url.loginAction}
        method="post"
        onSubmit={form.onSubmit}
      >
        <TextInput
          {...form.field("username", kcContext)}
          label={msgStr(
            !realm.loginWithEmailAllowed
              ? "username"
              : realm.registrationEmailAsUsername
                ? "email"
                : "usernameOrEmail",
          )}
          autoComplete="username"
          autoFocus
          required
        />
        <Button
          className="auth-submit"
          type="submit"
          disabled={form.submitting}
        >
          {msgStr(form.submitting ? "submitting" : "sendResetLink")}
        </Button>
      </form>
      <a className="text-center text-[13px]" href={url.loginUrl}>
        {msgStr("backToLogin")}
      </a>
    </AuthLayout>
  );
}
