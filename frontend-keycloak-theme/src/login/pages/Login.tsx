import { Button } from "@/shared/ui/button";
import { Checkbox } from "@/shared/ui/checkbox";
import { AuthLayout } from "../AuthLayout";
import { useKeycloakForm, TextInput, z } from "../form";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";

export default function Login({
  kcContext,
  i18n,
}: {
  kcContext: Extract<KcContext, { pageId: "login.ftl" }>;
  i18n: I18n;
}) {
  const {
    realm,
    url,
    login,
    auth,
    usernameHidden,
    social,
    registrationDisabled,
  } = kcContext;
  const { msgStr } = i18n;
  const form = useKeycloakForm(
    z.object({
      username: z.string(),
      password: z.string().min(1, msgStr("requiredField")),
    }),
    { username: login.username ?? "", password: "" },
  );
  const loginError = kcContext.messagesPerField.existsError(
    "username",
    "password",
  )
    ? kcContext.messagesPerField.getFirstError("username", "password")
    : undefined;
  return (
    <AuthLayout
      kcContext={kcContext}
      i18n={i18n}
      title={msgStr("loginAccountTitle")}
      description={msgStr("loginDescription")}
    >
      {realm.password && (
        <form
          id="kc-form-login"
          action={url.loginAction}
          method="post"
          onSubmit={form.onSubmit}
        >
          {!usernameHidden && (
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
              error={loginError ?? form.field("username", kcContext).error}
            />
          )}
          <TextInput
            {...form.field("password", kcContext)}
            type="password"
            label={msgStr("password")}
            autoComplete="current-password"
            autoFocus={!!usernameHidden}
            required
            error={loginError ?? form.field("password", kcContext).error}
          />
          {realm.resetPasswordAllowed && (
            <a
              className="self-end text-[13px]"
              href={url.loginResetCredentialsUrl}
            >
              {msgStr("doForgotPassword")}
            </a>
          )}
          {realm.rememberMe && !usernameHidden && (
            <Checkbox
              name="rememberMe"
              value="on"
              defaultChecked={!!login.rememberMe}
              label={msgStr("rememberMe")}
            />
          )}
          <input
            type="hidden"
            name="credentialId"
            value={auth.selectedCredential ?? ""}
          />
          <Button
            className="auth-submit"
            id="kc-login"
            type="submit"
            name="login"
            disabled={form.submitting}
          >
            {msgStr(form.submitting ? "submitting" : "doLogIn")}
          </Button>
        </form>
      )}
      {!!social?.providers?.length && (
        <div className="flex flex-col gap-3">
          <p className="text-center text-xs text-text-muted">
            {msgStr("identity-provider-login-label")}
          </p>
          {social.providers.map((provider) => (
            <a
              key={provider.alias}
              href={provider.loginUrl}
              className="rounded-sm border border-border px-3 py-3 text-center text-sm"
            >
              {msgStr("signInWith", provider.displayName)}
            </a>
          ))}
        </div>
      )}
      {realm.password && realm.registrationAllowed && !registrationDisabled && (
        <p className="text-center text-[13px] text-text-secondary">
          {msgStr("noAccount")}{" "}
          <a href={url.registrationUrl}>{msgStr("doRegister")}</a>
        </p>
      )}
    </AuthLayout>
  );
}
