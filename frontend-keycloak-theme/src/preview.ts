import { getKcContextMock } from "./login/KcPageStory";
import { themeMessages } from "./login/messages";

// Visual fixtures only; no fake authentication, email, or backend business state.
export function installPreview() {
  const pages = [
    "login.ftl",
    "register.ftl",
    "login-reset-password.ftl",
    "login-update-password.ftl",
    "info.ftl",
    "error.ftl",
  ] as const;
  const params = new URLSearchParams(location.search);
  const pageId =
    pages.find((page) => page === params.get("page")) ?? "login.ftl";
  const context = getKcContextMock({
    pageId,
    overrides: {
      locale: { currentLanguageTag: "vi" },
      realm: { registrationEmailAsUsername: true, loginWithEmailAllowed: true },
      url: {
        loginUrl: "?page=login.ftl",
        loginAction: "#preview",
        registrationUrl: "?page=register.ftl",
        registrationAction: "#preview",
        loginResetCredentialsUrl: "?page=login-reset-password.ftl",
      },
    },
  });
  if (context.pageId === "register.ftl") {
    context.profile.attributesByName = {
      email: context.profile.attributesByName.email,
    };
    context.recaptchaRequired = false;
    context.termsAcceptanceRequired = false;
  }
  if (context.pageId === "login.ftl") {
    context.social = undefined;
    context.login = {};
  }
  if (context.pageId === "info.ftl")
    context.message = {
      type: "success",
      summary: "Mật khẩu của bạn đã được cập nhật.",
    };
  if (context.pageId === "error.ftl")
    context.message = {
      type: "error",
      summary: "Liên kết đã hết hạn. Vui lòng bắt đầu lại từ ứng dụng.",
    };
  if (params.has("error") && !["info.ftl", "error.ftl"].includes(pageId))
    context.message = {
      type: "error",
      summary: "Thông tin chưa hợp lệ. Vui lòng kiểm tra lại.",
    };
  window.kcContext = context;
  const notice = document.createElement("p");
  notice.textContent = themeMessages.previewNotice;
  notice.style.cssText =
    "text-align:center;padding:12px;background:#FEF3C7;color:#92400E;font:13px sans-serif;margin:0";
  document.body.prepend(notice);
  HTMLFormElement.prototype.submit = function () {
    window.alert(themeMessages.previewNotice);
  };
  document.addEventListener("submit", (event) => event.preventDefault());
}
