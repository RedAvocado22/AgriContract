import { Suspense, lazy } from "react";
import type { ClassKey } from "keycloakify/login";
import type { KcContext } from "./KcContext";
import { useI18n } from "./i18n";
import DefaultPage from "keycloakify/login/DefaultPage";
import Template from "keycloakify/login/Template";
const UserProfileFormFields = lazy(
  () => import("keycloakify/login/UserProfileFormFields"),
);

const doMakeUserConfirmPassword = true;
const Login = lazy(() => import("./pages/Login"));
const Register = lazy(() => import("./pages/Register"));
const ResetPassword = lazy(() => import("./pages/ResetPassword"));
const UpdatePassword = lazy(() => import("./pages/UpdatePassword"));
const Info = lazy(() => import("./pages/Info"));
const ErrorPage = lazy(() => import("./pages/Error"));

export default function KcPage(props: { kcContext: KcContext }) {
  // Realm must also select vi so server-side errors use Vietnamese.
  const kcContext = props.kcContext.locale
    ? props.kcContext
    : {
        ...props.kcContext,
        locale: { currentLanguageTag: "vi", supported: [] },
      };

  const { i18n } = useI18n({ kcContext });

  return (
    <Suspense>
      {(() => {
        switch (kcContext.pageId) {
          case "login.ftl":
            return <Login kcContext={kcContext} i18n={i18n} />;
          case "register.ftl":
            return <Register kcContext={kcContext} i18n={i18n} />;
          case "login-reset-password.ftl":
            return <ResetPassword kcContext={kcContext} i18n={i18n} />;
          case "login-update-password.ftl":
            return <UpdatePassword kcContext={kcContext} i18n={i18n} />;
          case "info.ftl":
            return <Info kcContext={kcContext} i18n={i18n} />;
          case "error.ftl":
            return <ErrorPage kcContext={kcContext} i18n={i18n} />;
          default:
            return (
              <DefaultPage
                kcContext={kcContext}
                i18n={i18n}
                classes={classes}
                Template={Template}
                doUseDefaultCss={true}
                UserProfileFormFields={UserProfileFormFields}
                doMakeUserConfirmPassword={doMakeUserConfirmPassword}
              />
            );
        }
      })()}
    </Suspense>
  );
}

const classes = {} satisfies { [key in ClassKey]?: string };
