import { useState } from "react"
import { Link } from "react-router-dom"

import { PendingApprovalCard } from "@/features/auth/components/pending-approval-card"
import { LoginForm } from "@/features/auth/components/login-form"
import { RegisterForm } from "@/features/auth/components/register-form"
import { ForgotPasswordForm } from "@/features/auth/components/forgot-password-form"
import { ResetSuccessCard } from "@/features/auth/components/reset-success-card"

type AuthView = "login" | "register" | "pending" | "forgot_password" | "reset_success"

function BrandPanel({
  view,
  forgotStep = 1,
  identifier = "",
}: {
  view: "login" | "register" | "forgot_password"
  forgotStep?: number
  identifier?: string
}) {
  const maskIdentifier = (val: string) => {
    if (!val) return "•••"
    if (val.includes("@")) {
      const [local, domain] = val.split("@")
      return `${local.slice(0, 2)}•••@${domain}`
    }
    return `${val.slice(0, 4)} ••• ${val.slice(-3)}`
  }

  return (
    <div
      className="flex w-[400px] shrink-0 flex-col px-10 py-11"
      style={{ background: "linear-gradient(160deg, #15803D 0%, #0F5C2E 100%)" }}
    >
      <Link to="/" className="text-[19px] font-semibold text-white hover:opacity-90">
        AgriContract
      </Link>
      <div className="mt-1 text-[13px] text-[#BBF7D0]">Hợp đồng nông sản có ký quỹ</div>

      <div className="flex-1" />

      {view === "login" && (
        <>
          <div className="text-[26px] font-medium leading-[1.35] tracking-[-0.01em] text-white">
            Mua bán nông sản không lo bẻ kèo.
          </div>
          <div className="mt-4 text-sm leading-[1.6] text-[#DCFCE7]">
            Ký quỹ giữ tiền theo từng cột mốc, chỉ giải ngân khi hai bên xác nhận. Tranh chấp có
            giám định độc lập.
          </div>
          <div className="mt-8 flex gap-6">
            <div>
              <div className="font-mono text-[22px] font-semibold text-white">2.400+</div>
              <div className="text-[12px] text-[#BBF7D0]">hợp đồng đã tất toán</div>
            </div>
            <div>
              <div className="font-mono text-[22px] font-semibold text-white">98,2%</div>
              <div className="text-[12px] text-[#BBF7D0]">giao dịch không tranh chấp</div>
            </div>
          </div>
        </>
      )}

      {view === "register" && (
        <>
          <div className="text-[26px] font-medium leading-[1.35] tracking-[-0.01em] text-white">
            Xác minh một lần, giao dịch an tâm.
          </div>
          <div className="mt-4 text-sm leading-[1.6] text-[#DCFCE7]">
            Tài khoản cần quản trị viên duyệt hồ sơ trước khi giao dịch. Chọn đúng vai trò để
            mở đúng tính năng.
          </div>
          <div className="mt-8 flex flex-col gap-3.5">
            {[
              "Miễn phí mở tài khoản",
              "Ký quỹ giữ tiền theo cột mốc",
              "Quản trị viên duyệt hồ sơ trước khi kích hoạt",
            ].map((item) => (
              <div key={item} className="flex items-center gap-2.5 text-[13.5px] text-[#DCFCE7]">
                <span className="ms text-[18px] text-[#BBF7D0]">check_circle</span>
                {item}
              </div>
            ))}
          </div>
        </>
      )}

      {view === "forgot_password" && (
        <>
          {forgotStep === 1 ? (
            <>
              <div className="text-[24px] font-medium leading-[1.35] tracking-[-0.01em] text-white">
                Lấy lại quyền truy cập an toàn.
              </div>
              <div className="mt-4 text-sm leading-[1.6] text-[#DCFCE7]">
                Chúng tôi gửi mã xác nhận qua số điện thoại hoặc email đã đăng ký. Đổi mật khẩu không
                ảnh hưởng tới hợp đồng và ký quỹ đang chạy.
              </div>
            </>
          ) : (
            <div className="flex flex-col gap-3.5">
              <div className="flex items-center gap-2.5 text-[13.5px] text-[#DCFCE7]">
                <span className="ms text-[18px] text-[#BBF7D0]">check_circle</span>
                Đã gửi mã đến số {maskIdentifier(identifier)}
              </div>
              <div className="flex items-center gap-2.5 text-[13.5px] text-[#DCFCE7]">
                <span className="ms text-[18px] text-[#BBF7D0]">lock</span>
                Tối thiểu 8 ký tự, có chữ và số
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}

function AuthPage({ defaultView = "login" }: { defaultView?: AuthView }) {
  const [view, setView] = useState<AuthView>(defaultView)
  const [forgotStep, setForgotStep] = useState(1)
  const [forgotIdentifier, setForgotIdentifier] = useState("")

  // 1. Standalone views (không dùng layout card chung)
  if (view === "pending") {
    return (
      <div className="flex min-h-screen items-center justify-center bg-page-bg p-10">
        <PendingApprovalCard />
      </div>
    )
  }

  if (view === "reset_success") {
    return (
      <div className="flex min-h-screen items-center justify-center bg-page-bg p-10">
        <ResetSuccessCard onLogin={() => setView("login")} />
      </div>
    )
  }

  // 2. Unified 2-column card views (Login, Register & Forgot Password)
  return (
    <div className="flex min-h-screen items-center justify-center bg-page-bg p-10">
      <div
        className="flex overflow-hidden bg-surface rounded-auth-card shadow-auth-card"
        style={{
          width: 960,
          height: 620,
        }}
      >
        <BrandPanel
          view={view === "forgot_password" ? "forgot_password" : view === "register" ? "register" : "login"}
          forgotStep={forgotStep}
          identifier={forgotIdentifier}
        />
        <div className="flex flex-1 flex-col overflow-y-auto h-full no-scrollbar">
          {view === "login" && (
            <LoginForm
              onRegister={() => setView("register")}
              onForgotPassword={() => setView("forgot_password")}
            />
          )}
          {view === "register" && (
            <RegisterForm
              onSuccess={() => setView("pending")}
              onLogin={() => setView("login")}
            />
          )}
          {view === "forgot_password" && (
            <ForgotPasswordForm
              step={forgotStep}
              onStepChange={setForgotStep}
              onIdentifierChange={setForgotIdentifier}
              onSuccess={() => setView("reset_success")}
              onLogin={() => {
                setForgotStep(1)
                setView("login")
              }}
            />
          )}
        </div>
      </div>
    </div>
  )
}

export { AuthPage }
