/* eslint-disable @typescript-eslint/no-unused-vars */
import { i18nBuilder } from "keycloakify/login";
import type { ThemeName } from "../kc.gen";

/** @see: https://docs.keycloakify.dev/features/i18n */
const { useI18n, ofTypeI18n } = i18nBuilder
  .withThemeName<ThemeName>()
  .withExtraLanguages({
    vi: { label: "Tiếng Việt", getMessages: () => import("./i18n.vi") },
  })
  .withCustomTranslations({
    vi: {
      brandTagline: "Hợp đồng nông sản có ký quỹ",
      brandTitle: "Giao dịch cà phê, cùng xây dựng niềm tin.",
      brandDescription:
        "Kết nối bên mua và bên bán trên nền tảng hợp đồng nông sản AgriContract.",
      registerBrandTitle: "Bắt đầu với tài khoản của bạn.",
      registerBrandDescription:
        "Sau khi đăng nhập, bạn cần hoàn tất hồ sơ tổ chức và chờ xét duyệt trước khi thực hiện các giao dịch yêu cầu xác minh.",
      resetBrandTitle: "Lấy lại quyền truy cập an toàn.",
      resetBrandDescription:
        "Sử dụng liên kết gửi tới email đã đăng ký để đặt lại mật khẩu của bạn.",
      loginDescription:
        "Chào mừng trở lại. Đăng nhập để quản lý hợp đồng của bạn.",
      registerDescription: "Tạo tài khoản bằng email và mật khẩu của bạn.",
      resetDescription:
        "Nhập email hoặc tên đăng nhập đã đăng ký. Nếu tài khoản hợp lệ, bạn sẽ nhận được email hướng dẫn đặt lại mật khẩu.",
      updateDescription: "Chọn mật khẩu mới để bảo vệ tài khoản của bạn.",
      sendResetLink: "Gửi liên kết đặt lại mật khẩu",
      savePassword: "Đặt lại mật khẩu",
      alreadyHaveAccount: "Đã có tài khoản?",
      registrationNextStep:
        "Tạo tài khoản chưa đồng nghĩa với hồ sơ tổ chức đã được duyệt.",
      registrationUnavailable:
        "Hiện chưa thể đăng ký tài khoản tại đây. Vui lòng liên hệ quản trị viên để được hỗ trợ.",
      submitting: "Đang xử lý…",
      informationTitle: "Thông báo",
      missingContext:
        "Vui lòng mở trang này từ luồng đăng nhập của AgriContract.",
      previewNotice:
        "Bản xem trước giao diện — không thực hiện xác thực hoặc gửi email.",
      requiredField: "Vui lòng nhập thông tin này.",
      invalidEmail: "Vui lòng nhập địa chỉ email hợp lệ.",
      passwordMismatch: "Mật khẩu nhập lại không khớp.",
      signInWith: "Đăng nhập bằng {0}",
    },
    en: {
      brandTagline: "Hợp đồng nông sản có ký quỹ",
      brandTitle: "Giao dịch cà phê, cùng xây dựng niềm tin.",
      brandDescription:
        "Kết nối bên mua và bên bán trên nền tảng hợp đồng nông sản AgriContract.",
      registerBrandTitle: "Bắt đầu với tài khoản của bạn.",
      registerBrandDescription:
        "Sau khi đăng nhập, bạn cần hoàn tất hồ sơ tổ chức và chờ xét duyệt trước khi thực hiện các giao dịch yêu cầu xác minh.",
      resetBrandTitle: "Lấy lại quyền truy cập an toàn.",
      resetBrandDescription:
        "Sử dụng liên kết gửi tới email đã đăng ký để đặt lại mật khẩu của bạn.",
      loginDescription:
        "Chào mừng trở lại. Đăng nhập để quản lý hợp đồng của bạn.",
      registerDescription: "Tạo tài khoản bằng email và mật khẩu của bạn.",
      resetDescription:
        "Nhập email hoặc tên đăng nhập đã đăng ký. Nếu tài khoản hợp lệ, bạn sẽ nhận được email hướng dẫn đặt lại mật khẩu.",
      updateDescription: "Chọn mật khẩu mới để bảo vệ tài khoản của bạn.",
      sendResetLink: "Gửi liên kết đặt lại mật khẩu",
      savePassword: "Đặt lại mật khẩu",
      alreadyHaveAccount: "Đã có tài khoản?",
      registrationNextStep:
        "Tạo tài khoản chưa đồng nghĩa với hồ sơ tổ chức đã được duyệt.",
      registrationUnavailable:
        "Hiện chưa thể đăng ký tài khoản tại đây. Vui lòng liên hệ quản trị viên để được hỗ trợ.",
      submitting: "Đang xử lý…",
      informationTitle: "Thông báo",
      missingContext:
        "Vui lòng mở trang này từ luồng đăng nhập của AgriContract.",
      previewNotice:
        "Bản xem trước giao diện — không thực hiện xác thực hoặc gửi email.",
      requiredField: "Vui lòng nhập thông tin này.",
      invalidEmail: "Vui lòng nhập địa chỉ email hợp lệ.",
      passwordMismatch: "Mật khẩu nhập lại không khớp.",
      signInWith: "Đăng nhập bằng {0}",
    },
  })
  .build();

type I18n = typeof ofTypeI18n;

export { useI18n, type I18n };
