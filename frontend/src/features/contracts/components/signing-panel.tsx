import { useRef, useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useAuthStore } from "@/shared/auth/auth-store"
import { useFreshLogin } from "@/features/auth/hooks/use-fresh-login"
import { useOrganizationPermissions } from "@/features/organization/hooks/use-organization-permissions"
import { Banner } from "@/shared/ui/banner"
import { Button } from "@/shared/ui/button"
import { Checkbox } from "@/shared/ui/checkbox"
import { OTPInput } from "@/shared/ui/otp-input"
import { Countdown } from "@/shared/ui/countdown"
import { Stepper } from "@/shared/ui/stepper"
import { ContentHashDisplay } from "@/shared/ui/content-hash-display"
import { parseApiError } from "@/shared/api/errors"
import { contractBlockers, getSigningConsent } from "../api/capabilities"
import { validConsent } from "../api/signing"
import { SigningSession } from "../hooks/signing-session"
import {
  contractError,
  participantRole,
  vietnamTime,
} from "../types/presentation"
import type { Contract } from "../types/contract"

export function SigningPanel({ contract }: { contract: Contract }) {
  const { userId, roles } = useAuthStore()
  const role = participantRole(contract, userId ?? "", roles)
  const { canCreateOrSign, profile } = useOrganizationPermissions()
  const consent = useQuery({
    queryKey: ["contract-consent", userId, contract.id, contract.hash, role],
    queryFn: getSigningConsent,
    retry: false,
  })
  const fresh = useFreshLogin()
  const cache = useQueryClient()
  const [session] = useState(() =>
    role && contract.hash
      ? new SigningSession(contract.id, role, contract.hash)
      : null
  )
  const [accepted, setAccepted] = useState(false)
  const [otp, setOtp] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const [expired, setExpired] = useState(false)
  const [now, setNow] = useState(Date.now)
  const lock = useRef(false)
  const [resumed] = useState(
    () =>
      fresh.pendingIntent?.action === "sign" &&
      fresh.pendingIntent.contractId === contract.id
  )
  const allowed =
    canCreateOrSign &&
    role !== null &&
    profile.data?.role === role &&
    contract.generation === "coffee" &&
    ["OFFERED", "NEGOTIATING"].includes(contract.status) &&
    validConsent(consent.data ?? null, contract.id, contract.hash, role)
  async function act(kind: "initiate" | "resend" | "verify") {
    if (lock.current || !allowed || !accepted || !session) return
    lock.current = true
    setBusy(true)
    setError("")
    const intent = { action: "sign", contractId: contract.id, step: kind }
    try {
      fresh.consumeIntent("sign")
      if (!(await fresh.ensureFreshLogin(intent))) return
      if (kind === "verify") {
        await session.verify(otp)
        setOtp("")
        await Promise.all([
          cache.invalidateQueries({
            queryKey: ["contract", userId, contract.id],
          }),
          cache.invalidateQueries({ queryKey: ["contracts", userId] }),
        ])
      } else {
        setOtp("")
        setExpired(false)
        await session.initiate(consent.data ?? null, kind === "resend")
        setNow(Date.now())
      }
    } catch (failure) {
      setError(contractError(failure))
      if (await fresh.handleStepUpError(failure, intent)) return
      const { code } = parseApiError(failure)
      if (
        [
          "PRESENTED_CONTENT_HASH_MISMATCH",
          "OTP_TERMS_CHANGED",
          "CONSENT_STATEMENT_OUTDATED",
          "CONTRACT_ALREADY_SIGNED_BY_ACTOR",
        ].includes(code)
      ) {
        setAccepted(false)
        setOtp("")
        await cache.invalidateQueries({
          queryKey: ["contract", userId, contract.id],
        })
        await consent.refetch()
      }
    } finally {
      lock.current = false
      setBusy(false)
    }
  }
  return (
    <section
      className="space-y-4 rounded-md border border-border bg-surface p-4"
      aria-label="Ký hợp đồng"
    >
      <h2 className="text-lg">Ký hợp đồng bằng OTP</h2>
      <Stepper
        steps={["Đọc & đồng ý", "Nhận mã", "Xác nhận"]}
        currentStep={session?.complete ? 3 : session?.challenge ? 2 : 1}
      />
      {resumed && (
        <Banner variant="info">
          Đã quay lại bước ký sau xác thực. Hãy đọc lại điều khoản và xác nhận;
          hệ thống không tự gửi lại thao tác hoặc lưu mã OTP.
        </Banner>
      )}
      {!canCreateOrSign && (
        <Banner variant="warning">
          Chỉ hồ sơ đã xác minh và đúng vai trò bên tham gia mới được ký.
        </Banner>
      )}
      {!consent.data && (
        <Banner variant="warning">{contractBlockers.signing}</Banner>
      )}
      {contract.hash && (
        <ContentHashDisplay
          className="max-w-full break-all"
          hash={contract.hash}
        />
      )}
      {consent.data ? (
        <Checkbox
          label={consent.data.text}
          checked={accepted}
          disabled={!allowed || busy}
          onCheckedChange={setAccepted}
        />
      ) : (
        <Checkbox
          label="Chờ nội dung cam kết từ máy chủ"
          disabled
          checked={false}
        />
      )}
      {consent.data && (
        <p className="text-xs">Phiên bản cam kết: {consent.data.version}</p>
      )}
      {error && (
        <Banner role="alert" variant="danger">
          {error}
        </Banner>
      )}
      {session?.complete ? (
        <Banner variant="success">
          Máy chủ đã xác nhận chữ ký của bạn. Trạng thái của bên còn lại và kích
          hoạt được đọc lại từ hợp đồng.
        </Banner>
      ) : session?.challenge ? (
        <div className="space-y-3">
          <p className="text-sm">
            Mã hết hạn lúc {vietnamTime(session.challenge.expiresAt)}
          </p>
          <Countdown
            key={session.challenge.otpId}
            seconds={Math.max(
              0,
              Math.ceil((Date.parse(session.challenge.expiresAt) - now) / 1000)
            )}
            onExpire={() => setExpired(true)}
          />
          <fieldset
            disabled={busy || expired || !allowed}
            aria-label="Nhập mã OTP"
          >
            <OTPInput
              value={otp}
              onChange={setOtp}
              className="gap-1 [&_input]:min-w-0 [&_input]:flex-1 [&_input]:max-w-10"
            />
          </fieldset>
          {expired && <p role="status">Mã đã hết hạn. Vui lòng gửi mã mới.</p>}
          <Button
            disabled={busy || expired || !allowed || otp.length !== 6}
            onClick={() => void act("verify")}
          >
            Xác nhận chữ ký
          </Button>
          <Button
            variant="outline"
            disabled={busy || !allowed || Date.now() < session.resendAfter}
            onClick={() => void act("resend")}
          >
            Gửi lại mã
          </Button>
          {session.resendAfter > now && (
            <Countdown
              key={`resend-${session.resendAfter}`}
              seconds={Math.ceil((session.resendAfter - now) / 1000)}
              onExpire={() => setNow(Date.now())}
              className="text-sm"
            />
          )}
        </div>
      ) : (
        <Button
          disabled={
            busy ||
            !allowed ||
            !accepted ||
            !session ||
            Date.now() < session.resendAfter
          }
          onClick={() => void act("initiate")}
        >
          {busy ? "Đang xử lý…" : "Gửi mã OTP"}
        </Button>
      )}
      {session && !session.challenge && session.resendAfter > now && (
        <Countdown
          key={`retry-${session.resendAfter}`}
          seconds={Math.max(0, Math.ceil((session.resendAfter - now) / 1000))}
          onExpire={() => setNow(Date.now())}
          className="text-sm"
        />
      )}
    </section>
  )
}
