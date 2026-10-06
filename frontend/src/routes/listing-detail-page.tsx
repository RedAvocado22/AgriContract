import { useState } from "react"
import { Link, useParams } from "react-router-dom"

import { cn } from "@/shared/lib/utils"
import { buttonVariants } from "@/shared/ui/button"
import { Footer } from "@/shared/ui/footer"
import { PublicNav } from "@/shared/ui/public-nav"
import { StatusBadge } from "@/shared/ui/status-badge"

// ─── Types ────────────────────────────────────────────────────────────────────

type Commodity = "COFFEE" | "RICE" | "RUBBER" | "CASHEW"
type GeoVerificationStatus = "PENDING" | "CHECKED" | "UNVERIFIED"
type GeoRiskLevel = "LOW_RISK" | "HIGH_RISK" | "INCONCLUSIVE"
type VerificationLevel = "SELF_DECLARED" | "CADASTRAL_BACKED"
type GeometryType = "POINT" | "POLYGON"
type GeoSource = "KML_IMPORT" | "MANUAL_PIN"

interface ListingDetail {
  id: string
  productName: string
  location: string
  season: string
  commodity: Commodity
  // Specs
  variety: string
  standard: string
  moisture: string
  impurity: string
  remainingTons: number
  minBatchTons: number
  // Price
  pricePerKg: number
  referencePrice: number
  // Description
  description: string
  // EUDR — chỉ có với COFFEE / RUBBER
  geo?: {
    verificationStatus: GeoVerificationStatus
    riskLevel?: GeoRiskLevel
    verificationLevel: VerificationLevel
    geometryType: GeometryType
    source: GeoSource
    province: string
  }
  // Seller
  seller: {
    name: string
    type: string
    province: string
    reputation: number
    completedContracts: number
    onTimeRate: number
    memberSince: number
    userId: string
  }
  // Gallery — placeholder count (§5 blocker: ảnh thật chưa có)
  photoCount: number
}

// ─── Static data ──────────────────────────────────────────────────────────────

const COMMODITY_LABEL: Record<Commodity, string> = {
  COFFEE: "Cà phê",
  RUBBER: "Cao su",
  RICE: "Gạo",
  CASHEW: "Điều",
}

const EUDR_COMMODITIES: Commodity[] = ["COFFEE", "RUBBER"]

const GEO_VERIFICATION_LABEL: Record<GeoVerificationStatus, string> = {
  PENDING: "Đang kiểm tra",
  CHECKED: "Đã kiểm tra",
  UNVERIFIED: "Chưa xác minh được",
}

const GEO_RISK_LABEL: Record<GeoRiskLevel, string> = {
  LOW_RISK: "Rủi ro thấp",
  HIGH_RISK: "Rủi ro cao",
  INCONCLUSIVE: "Không kết luận được",
}

const VERIFICATION_LEVEL_LABEL: Record<VerificationLevel, string> = {
  SELF_DECLARED: "Tự khai",
  CADASTRAL_BACKED: "Có trích lục địa chính",
}

const GEOMETRY_TYPE_LABEL: Record<GeometryType, string> = {
  POINT: "Điểm (POINT)",
  POLYGON: "Vùng (POLYGON)",
}

const GEO_SOURCE_LABEL: Record<GeoSource, string> = {
  KML_IMPORT: "Nhập từ file KML",
  MANUAL_PIN: "Ghim thủ công",
}

// Mock data — dùng bộ số liệu mẫu từ context brief
const LISTINGS: Record<string, ListingDetail> = {
  L001: {
    id: "L001",
    productName: "Robusta sàng 18",
    location: "Ea Kar, Đắk Lắk",
    season: "niên vụ 2025–2026",
    commodity: "COFFEE",
    variety: "Robusta sàng 18",
    standard: "TCVN 4193:2014",
    moisture: "≤ 13%",
    impurity: "≤ 1%",
    remainingTons: 40,
    minBatchTons: 5,
    pricePerKg: 118500,
    referencePrice: 118500,
    description:
      "Cà phê Robusta sàng 18 thu hái chọn lọc, phơi trên giàn, độ ẩm ổn định dưới 13%. Vùng trồng Ea Kar đã có trích lục địa chính và khai toạ độ đầy đủ theo yêu cầu EUDR. Sẵn hàng giao theo nhiều đợt, ưu tiên hợp đồng kỳ hạn có ký quỹ.",
    geo: {
      verificationStatus: "CHECKED",
      riskLevel: "LOW_RISK",
      verificationLevel: "CADASTRAL_BACKED",
      geometryType: "POLYGON",
      source: "KML_IMPORT",
      province: "Đắk Lắk",
    },
    seller: {
      name: "HTX Nông nghiệp Ea Kar",
      type: "Hợp tác xã",
      province: "Đắk Lắk",
      reputation: 88,
      completedContracts: 32,
      onTimeRate: 94,
      memberSince: 2023,
      userId: "htx-ea-kar",
    },
    photoCount: 4,
  },
  L002: {
    id: "L002",
    productName: "Arabica Cầu Đất",
    location: "Lạc Dương, Lâm Đồng",
    season: "niên vụ 2025–2026",
    commodity: "COFFEE",
    variety: "Arabica Cầu Đất",
    standard: "TCVN 4193:2014",
    moisture: "≤ 12.5%",
    impurity: "≤ 0.5%",
    remainingTons: 12,
    minBatchTons: 2,
    pricePerKg: 195000,
    referencePrice: 190000,
    description:
      "Arabica Cầu Đất trồng ở độ cao 1.500m, thu hái chín đỏ, sơ chế ướt. Hương thơm đặc trưng, độ axit cân bằng. Phù hợp xuất khẩu specialty coffee.",
    geo: {
      verificationStatus: "CHECKED",
      riskLevel: "HIGH_RISK",
      verificationLevel: "SELF_DECLARED",
      geometryType: "POLYGON",
      source: "KML_IMPORT",
      province: "Lâm Đồng",
    },
    seller: {
      name: "HTX Cà phê Cầu Đất",
      type: "Hợp tác xã",
      province: "Lâm Đồng",
      reputation: 81,
      completedContracts: 18,
      onTimeRate: 89,
      memberSince: 2024,
      userId: "htx-cau-dat",
    },
    photoCount: 3,
  },
  L004: {
    id: "L004",
    productName: "Gạo ST25",
    location: "Ngã Năm, Sóc Trăng",
    season: "vụ Đông Xuân 2025–2026",
    commodity: "RICE",
    variety: "ST25",
    standard: "TCVN 11888:2017",
    moisture: "≤ 14%",
    impurity: "≤ 0.1%",
    remainingTons: 80,
    minBatchTons: 10,
    pricePerKg: 21500,
    referencePrice: 21000,
    description:
      "Gạo ST25 nguyên liệu từ vùng ngọt Ngã Năm, canh tác theo hướng VietGAP. Hạt dài, trong, mùi thơm nhẹ. Phù hợp xuất khẩu và chế biến.",
    seller: {
      name: "HTX Lúa gạo Ngã Năm",
      type: "Hợp tác xã",
      province: "Sóc Trăng",
      reputation: 90,
      completedContracts: 45,
      onTimeRate: 97,
      memberSince: 2022,
      userId: "htx-nga-nam",
    },
    photoCount: 3,
  },
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatPrice(n: number) {
  return n.toLocaleString("vi-VN") + " ₫"
}

function reputationVariant(score: number): "success" | "warning" | "danger" {
  if (score >= 80) return "success"
  if (score >= 60) return "warning"
  return "danger"
}

function geoVerificationVariant(
  s: GeoVerificationStatus
): "success" | "neutral" | "warning" {
  if (s === "CHECKED") return "success"
  if (s === "PENDING") return "neutral"
  return "warning"
}

function geoRiskVariant(r: GeoRiskLevel): "success" | "danger" | "warning" {
  if (r === "LOW_RISK") return "success"
  if (r === "HIGH_RISK") return "danger"
  return "warning"
}


// ─── Gallery ─────────────────────────────────────────────────────────────────

const HATCH_DARK =
  "repeating-linear-gradient(135deg,#E4EAE3,#E4EAE3 10px,#EDF1EC 10px,#EDF1EC 20px)"
const HATCH_LIGHT =
  "repeating-linear-gradient(135deg,#EDF1EC,#EDF1EC 8px,#F1F5F4 8px,#F1F5F4 16px)"

function Gallery({
  commodity,
  photoCount,
  activeIdx,
  onSelect,
}: {
  commodity: Commodity
  photoCount: number
  activeIdx: number
  onSelect: (i: number) => void
}) {
  return (
    <div className="mb-7">
      {/* Main image */}
      <div
        className="mb-3 flex h-[300px] items-end justify-between rounded-[12px] border-[0.5px] border-border p-4"
        style={{ background: HATCH_DARK }}
        aria-label={`Ảnh lô hàng ${COMMODITY_LABEL[commodity]}`}
      >
        <span className="rounded-[6px] bg-text-primary px-2.5 py-1 font-mono text-xs text-white">
          {COMMODITY_LABEL[commodity]}
        </span>
        <span className="rounded-[6px] bg-text-primary/55 px-2.5 py-1 font-mono text-[11.5px] text-white">
          Ảnh lô hàng · {photoCount} ảnh
        </span>
      </div>

      {/* Thumbnails */}
      <div className="flex gap-2.5">
        {Array.from({ length: photoCount }).map((_, i) => (
          <button
            key={i}
            id={`gallery-thumb-${i}`}
            onClick={() => onSelect(i)}
            aria-label={`Xem ảnh ${i + 1}`}
            className={cn(
              "h-16 w-24 flex-shrink-0 rounded-lg border transition-all",
              i === activeIdx
                ? "border-primary border"
                : "border-[0.5px] border-border"
            )}
            style={{ background: i === activeIdx ? HATCH_DARK : HATCH_LIGHT }}
          />
        ))}
      </div>
    </div>
  )
}

// ─── SpecGrid ─────────────────────────────────────────────────────────────────

function SpecRow({
  label,
  value,
  mono = false,
  border = true,
  side,
}: {
  label: string
  value: string
  mono?: boolean
  border?: boolean
  side: "left" | "right"
}) {
  return (
    <div
      className={cn(
        "flex items-center justify-between py-2.5 text-[13.5px]",
        border && "border-b-[0.5px] border-border",
        side === "left" ? "pr-6" : "pl-6"
      )}
    >
      <span className="text-text-secondary">{label}</span>
      <span className={cn(mono && "font-mono")}>{value}</span>
    </div>
  )
}

function SpecCard({ listing }: { listing: ListingDetail }) {
  return (
    <div className="mb-5 rounded-[12px] border-[0.5px] border-border bg-surface px-6 py-5">
      <div className="mb-3.5 text-base font-medium">Thông số sản phẩm</div>
      <div className="grid grid-cols-2">
        <SpecRow label="Loại" value={listing.variety} side="left" />
        <SpecRow label="Tiêu chuẩn" value={listing.standard} mono side="right" />
        <SpecRow label="Độ ẩm" value={listing.moisture} side="left" />
        <SpecRow label="Tạp chất" value={listing.impurity} side="right" />
        <SpecRow
          label="Khối lượng còn lại"
          value={`${listing.remainingTons} tấn`}
          mono
          border={false}
          side="left"
        />
        <SpecRow
          label="Đợt giao tối thiểu"
          value={`${listing.minBatchTons} tấn`}
          mono
          border={false}
          side="right"
        />
      </div>
    </div>
  )
}

// ─── EudrCard ─────────────────────────────────────────────────────────────────

function EudrCard({ geo, commodity }: { geo: NonNullable<ListingDetail["geo"]>; commodity: Commodity }) {
  return (
    <div className="mb-5 rounded-[12px] border-[0.5px] border-border bg-surface px-6 py-5">
      <div className="mb-3.5 flex items-center justify-between">
        <div className="text-base font-medium">Truy xuất vùng trồng (EUDR)</div>
        <span className="text-[12.5px] text-text-muted">
          {COMMODITY_LABEL[commodity]} thuộc EUDR
        </span>
      </div>

      <div className="flex items-stretch gap-4">
        {/* Map placeholder */}
        <div
          className="flex w-[180px] flex-shrink-0 items-center justify-center rounded-[10px] border-[0.5px] border-border font-mono text-xs text-text-muted"
          style={{
            background:
              "repeating-linear-gradient(135deg,#E9EFEA,#E9EFEA 9px,#F1F5F4 9px,#F1F5F4 18px)",
          }}
          aria-label="Bản đồ vùng trồng (chưa tích hợp map)"
        >
          bản đồ vùng trồng
        </div>

        <div className="flex-1">
          {/* Badges — 2 tách bạch: tiến trình + kết quả */}
          <div className="mb-3 flex flex-wrap gap-2">
            <StatusBadge
              variant={geoVerificationVariant(geo.verificationStatus)}
              className="text-xs"
            >
              {GEO_VERIFICATION_LABEL[geo.verificationStatus]}
            </StatusBadge>
            {geo.verificationStatus === "CHECKED" && geo.riskLevel && (
              <StatusBadge
                variant={geoRiskVariant(geo.riskLevel)}
                className="text-xs"
              >
                {GEO_RISK_LABEL[geo.riskLevel]}
              </StatusBadge>
            )}
            <StatusBadge variant="neutral" className="text-xs">
              {VERIFICATION_LEVEL_LABEL[geo.verificationLevel]}
            </StatusBadge>
          </div>

          {/* Key-value */}
          <div className="flex flex-col gap-2">
            <div className="flex justify-between text-[13.5px]">
              <span className="text-text-secondary">Kiểu hình học</span>
              <span>{GEOMETRY_TYPE_LABEL[geo.geometryType]}</span>
            </div>
            <div className="flex justify-between text-[13.5px]">
              <span className="text-text-secondary">Tỉnh khai báo</span>
              <span>{geo.province}</span>
            </div>
            <div className="flex justify-between text-[13.5px]">
              <span className="text-text-secondary">Nguồn dữ liệu</span>
              <span>{GEO_SOURCE_LABEL[geo.source]}</span>
            </div>
          </div>

          {/* Disclaimer — theo §3.8 */}
          <p className="mt-3 text-xs leading-[1.55] text-text-muted">
            Tiến trình kiểm tra và mức rủi ro là hai chỉ số tách biệt —
            &ldquo;đã kiểm tra&rdquo; là trạng thái, &ldquo;rủi ro thấp&rdquo; là kết quả.
          </p>
        </div>
      </div>
    </div>
  )
}

// ─── DescriptionCard ──────────────────────────────────────────────────────────

function DescriptionCard({ text }: { text: string }) {
  return (
    <div className="rounded-[12px] border-[0.5px] border-border bg-surface px-6 py-5">
      <div className="mb-2.5 text-base font-medium">Mô tả từ bên bán</div>
      <p className="text-sm leading-[1.7] text-text-secondary">{text}</p>
    </div>
  )
}

// ─── PriceCtaCard ─────────────────────────────────────────────────────────────

function PriceCtaCard({ listing }: { listing: ListingDetail }) {
  const diff = listing.pricePerKg - listing.referencePrice
  const diffSign = diff >= 0 ? "+" : ""

  return (
    <div className="rounded-[12px] border-[0.5px] border-border bg-surface px-6 py-[22px]">
      <div className="mb-0.5 text-[13px] text-text-secondary">Giá chào bán</div>
      <div className="mb-1 flex items-baseline gap-2">
        <span className="font-mono text-[28px] font-medium">
          {formatPrice(listing.pricePerKg)}
        </span>
        <span className="text-sm text-text-secondary">/ kg</span>
      </div>
      <div className="mb-[18px] text-[12.5px] text-text-muted">
        Giá tham khảo VNSAT hôm nay:{" "}
        <span className="font-mono">{formatPrice(listing.referencePrice)}/kg</span>
        {diff !== 0 && (
          <span
            className={cn(
              "ml-1.5 font-mono",
              diff > 0 ? "text-warning" : "text-success"
            )}
          >
            ({diffSign}{formatPrice(Math.abs(diff))})
          </span>
        )}
      </div>

      <Link
        to="/login"
        id="listing-detail-login-cta"
        className="mb-2.5 flex h-[46px] items-center justify-center rounded-[9px] bg-primary text-[15px] font-medium text-white transition-opacity hover:opacity-90"
      >
        Đăng nhập để gửi đề nghị
      </Link>
      <Link
        to="/register"
        id="listing-detail-register-cta"
        className="flex h-[44px] items-center justify-center rounded-[9px] border-[0.5px] border-border text-sm font-medium text-text-primary transition-colors hover:bg-surface-muted"
      >
        Đăng ký doanh nghiệp
      </Link>

      <div className="mt-4 flex items-start gap-2 rounded-[9px] bg-surface-muted px-3.5 py-3">
        <span className="ms mt-px text-[17px] text-text-secondary">lock</span>
        <span className="text-xs leading-[1.55] text-text-secondary">
          Giao dịch qua ký quỹ: tiền được khoá và giải ngân theo từng cột mốc
          giao hàng.
        </span>
      </div>
    </div>
  )
}

// ─── SellerCard ───────────────────────────────────────────────────────────────

function SellerCard({ seller }: { seller: ListingDetail["seller"] }) {
  return (
    <div className="rounded-[12px] border-[0.5px] border-border bg-surface px-6 py-5">
      <div className="mb-3 text-[13px] text-text-secondary">Bên bán</div>

      {/* Seller identity */}
      <div className="mb-4 flex items-center gap-3">
        <div className="size-11 flex-shrink-0 rounded-full border-[0.5px] border-border bg-surface-muted" />
        <div className="flex-1">
          <Link
            to={`/reputation/${seller.userId}`}
            className="text-[15px] font-medium hover:text-primary hover:underline"
          >
            {seller.name}
          </Link>
          <div className="text-[12.5px] text-text-secondary">
            {seller.type} · {seller.province}
          </div>
        </div>
        <StatusBadge
          variant={reputationVariant(seller.reputation)}
          className="text-sm px-2.5"
        >
          {seller.reputation}
        </StatusBadge>
      </div>

      {/* Stats */}
      <div className="mb-4 flex flex-col gap-2.5">
        <div className="flex justify-between text-[13px]">
          <span className="text-text-secondary">Hợp đồng hoàn thành</span>
          <span className="font-mono">{seller.completedContracts}</span>
        </div>
        <div className="flex justify-between text-[13px]">
          <span className="text-text-secondary">Tỷ lệ đúng hạn</span>
          <span className="font-mono">{seller.onTimeRate}%</span>
        </div>
        <div className="flex justify-between text-[13px]">
          <span className="text-text-secondary">Thành viên từ</span>
          <span className="font-mono">{seller.memberSince}</span>
        </div>
      </div>

      <Link
        to={`/reputation/${seller.userId}`}
        id={`seller-reputation-link-${seller.userId}`}
        className="block rounded-lg border-[0.5px] border-border py-2.5 text-center text-[13.5px] font-medium text-text-primary transition-colors hover:bg-surface-muted"
      >
        Xem hồ sơ uy tín công khai
      </Link>
    </div>
  )
}

// ─── NotFound ─────────────────────────────────────────────────────────────────

function NotFound() {
  return (
    <div className="flex flex-col items-center gap-3 py-24 text-center">
      <span className="ms text-[40px] text-text-muted">search_off</span>
      <div className="text-[15px] font-medium">Không tìm thấy listing</div>
      <div className="text-sm text-text-secondary">
        Listing này không tồn tại hoặc đã được gỡ xuống.
      </div>
      <Link
        to="/listing"
        className={cn(buttonVariants({ variant: "outline" }), "mt-2")}
      >
        Quay lại sàn nguồn hàng
      </Link>
    </div>
  )
}

// ─── ListingDetailPage ────────────────────────────────────────────────────────

function ListingDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [activePhotoIdx, setActivePhotoIdx] = useState(0)

  const listing = id ? LISTINGS[id] : undefined

  const isEudr = listing ? EUDR_COMMODITIES.includes(listing.commodity) : false

  return (
    <div className="min-h-screen bg-page-bg text-text-primary">
      {listing && (
        <title>
          {listing.productName} — {listing.seller.name} — AgriContract
        </title>
      )}
      <PublicNav />

      <div className="mx-auto max-w-[1120px] px-10 pt-6 pb-20">
        {!listing ? (
          <NotFound />
        ) : (
          <>
            {/* Breadcrumb */}
            <nav
              className="mb-[18px] flex items-center gap-1.5 text-[13px] text-text-secondary"
              aria-label="Breadcrumb"
            >
              <Link to="/listing" className="text-primary hover:underline">
                Sàn nguồn hàng
              </Link>
              <span className="text-border-strong">/</span>
              <span>{COMMODITY_LABEL[listing.commodity]}</span>
              <span className="text-border-strong">/</span>
              <span className="text-text-primary">{listing.productName}</span>
            </nav>

            {/* 2-column layout */}
            <div className="grid grid-cols-[1fr_372px] items-start gap-7 max-[1023px]:grid-cols-1">
              {/* ── Left col ── */}
              <div>
                <Gallery
                  commodity={listing.commodity}
                  photoCount={listing.photoCount}
                  activeIdx={activePhotoIdx}
                  onSelect={setActivePhotoIdx}
                />

                <h1 className="mb-1 text-[24px] font-medium">
                  {listing.productName}
                </h1>
                <p className="mb-6 text-sm text-text-secondary">
                  {listing.location} · {listing.season}
                </p>

                <SpecCard listing={listing} />

                {isEudr && listing.geo && (
                  <EudrCard geo={listing.geo} commodity={listing.commodity} />
                )}

                <DescriptionCard text={listing.description} />
              </div>

              {/* ── Right rail (sticky) ── */}
              <div
                className="flex flex-col gap-4 max-[1023px]:order-first"
                style={{ position: "sticky", top: "88px" }}
              >
                <PriceCtaCard listing={listing} />
                <SellerCard seller={listing.seller} />
              </div>
            </div>
          </>
        )}
      </div>

      <Footer />
    </div>
  )
}

export { ListingDetailPage }
