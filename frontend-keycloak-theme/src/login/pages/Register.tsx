import { Button } from "@/shared/ui/button";
import { Banner } from "@/shared/ui/banner";
import { AuthLayout } from "../AuthLayout";
import { useKeycloakForm, TextInput, z } from "../form";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";

export default function Register({
  kcContext,
  i18n,
}: {
  kcContext: Extract<KcContext, { pageId: "register.ftl" }>;
  i18n: I18n;
}) {
  const { msgStr } = i18n;
  const { url, profile, passwordRequired } = kcContext;
  const schema = z
    .object({
      email: z.email(msgStr("invalidEmail")),
      password: z
        .string()
        .min(passwordRequired ? 1 : 0, msgStr("requiredField")),
      "password-confirm": z.string(),
    })
    .refine((data) => data.password === data["password-confirm"], {
      message: msgStr("passwordMismatch"),
      path: ["password-confirm"],
    });
  const form = useKeycloakForm(schema, {
    email: profile.attributesByName.email?.value ?? "",
    password: "",
    "password-confirm": "",
  });
  // Do not hide mandatory server requirements or invent first/last names to bypass them.
  const unsupportedProfile = Object.values(profile.attributesByName).some(
    (attribute) =>
      attribute.required && !["email", "username"].includes(attribute.name),
  );
  const unavailable =
    !kcContext.realm.registrationEmailAsUsername ||
    unsupportedProfile ||
    kcContext.recaptchaRequired ||
    kcContext.termsAcceptanceRequired;
  return (
    <AuthLayout
      kcContext={kcContext}
      i18n={i18n}
      title={msgStr("registerTitle")}
      description={msgStr("registerDescription")}
    >
      {unavailable ? (
        <Banner variant="warning" role="alert">
          {msgStr("registrationUnavailable")}
        </Banner>
      ) : (
        <form
          id="kc-register-form"
          action={url.registrationAction}
          method="post"
          onSubmit={form.onSubmit}
        >
          <TextInput
            {...form.field("email", kcContext)}
            type="email"
            label={msgStr("email")}
            autoComplete="email"
            autoFocus
            required
          />
          {passwordRequired && (
            <>
              <TextInput
                {...form.field("password", kcContext)}
                type="password"
                label={msgStr("password")}
                autoComplete="new-password"
                required
              />
              <TextInput
                {...form.field("password-confirm", kcContext)}
                type="password"
                label={msgStr("passwordConfirm")}
                autoComplete="new-password"
                required
              />
            </>
          )}
          <Banner variant="info">{msgStr("registrationNextStep")}</Banner>
          <Button
            className="auth-submit"
            type="submit"
            disabled={form.submitting}
          >
            {msgStr(form.submitting ? "submitting" : "doRegister")}
          </Button>
        </form>
      )}
      <p className="text-center text-[13px] text-text-secondary">
        {msgStr("alreadyHaveAccount")}{" "}
        <a href={url.loginUrl}>{msgStr("doLogIn")}</a>
      </p>
    </AuthLayout>
  );
}
