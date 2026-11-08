import { Button } from "@/shared/ui/button";
import { Checkbox } from "@/shared/ui/checkbox";
import { AuthLayout } from "../AuthLayout";
import { useKeycloakForm, TextInput, z } from "../form";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";

export default function UpdatePassword({
  kcContext,
  i18n,
}: {
  kcContext: Extract<KcContext, { pageId: "login-update-password.ftl" }>;
  i18n: I18n;
}) {
  const { msgStr } = i18n;
  const schema = z
    .object({
      "password-new": z.string().min(1, msgStr("requiredField")),
      "password-confirm": z.string(),
    })
    .refine((data) => data["password-new"] === data["password-confirm"], {
      message: msgStr("passwordMismatch"),
      path: ["password-confirm"],
    });
  const form = useKeycloakForm(schema, {
    "password-new": "",
    "password-confirm": "",
  });
  return (
    <AuthLayout
      kcContext={kcContext}
      i18n={i18n}
      title={msgStr("updatePasswordTitle")}
      description={msgStr("updateDescription")}
    >
      <form
        id="kc-passwd-update-form"
        action={kcContext.url.loginAction}
        method="post"
        onSubmit={form.onSubmit}
      >
        <TextInput
          {...form.field("password-new", kcContext, "password")}
          type="password"
          label={msgStr("passwordNew")}
          autoComplete="new-password"
          autoFocus
          required
        />
        <TextInput
          {...form.field("password-confirm", kcContext)}
          type="password"
          label={msgStr("passwordNewConfirm")}
          autoComplete="new-password"
          required
        />
        <Checkbox
          name="logout-sessions"
          value="on"
          defaultChecked
          label={msgStr("logoutOtherSessions")}
        />
        <Button
          className="auth-submit"
          type="submit"
          disabled={form.submitting}
        >
          {msgStr(form.submitting ? "submitting" : "savePassword")}
        </Button>
      </form>
      {kcContext.isAppInitiatedAction && (
        <form action={kcContext.url.loginAction} method="post">
          <Button
            type="submit"
            variant="outline"
            name="cancel-aia"
            value="true"
          >
            {msgStr("doCancel")}
          </Button>
        </form>
      )}
    </AuthLayout>
  );
}
