import { useState } from "react"
import { Link } from "react-router-dom"

import { cn } from "@/shared/lib/utils"
import { buttonVariants } from "@/shared/ui/button"
import { Footer } from "@/shared/ui/footer"
import { Pagination } from "@/shared/ui/pagination"
import { PublicNav } from "@/shared/ui/public-nav"
import { StatusBadge } from "@/shared/ui/status-badge"

// ─── Types ────────────────────────────────────────────────────────────────────

type Commodity = "COFFEE" | "RICE" | "RUBBER" | "CASHEW"
type GeoVerificationStatus = "PENDING" | "CHECKED" | "UNVERIFIED"
type GeoRiskLevel = "LOW_RISK" | "HIGH_RISK" | "INCONCLUSIVE"
type SortKey = "reputation" | "price_asc" | "price_desc" | "quantity"

interface Listing {
  id: string
  productName: string
  province: string
  sellerName: string
  sellerReputation: number
  commodity: Commodity
  remainingTons: number
  pricePerKg: number
  /** Only for COFFEE and RUBBER */
  geoVerificationStatus?: GeoVerificationStatus
  geoRiskLevel?: GeoRiskLevel
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
  PENDING: "Đang kiểm tra vùng trồng",
  CHECKED: "Đã kiểm tra",
  UNVERIFIED: "Chưa xác minh được",
}

const GEO_RISK_LABEL: Record<GeoRiskLevel, string> = {
  LOW_RISK: "Rủi ro thấp",
  HIGH_RISK: "Rủi ro cao",
  INCONCLUSIVE: "Không kết luận được",
}

const PROVINCE_OPTIONS = [
  "Đắk Lắk",
  "Lâm Đồng",
  "Tây Ninh",
  "Sóc Trăng",
  "Bình Phước",
]

const REPUTATION_OPTIONS = [
  { label: "Tất cả", value: 0 },
  { label: "Uy tín ≥ 80", value: 80 },
  { label: "Uy tín ≥ 85", value: 85 },
  { label: "Uy tín ≥ 90", value: 90 },
]

const EUDR_RISK_OPTIONS: Array<{ label: string; value: GeoRiskLevel | "" }> = [
  { label: "Tất cả", value: "" },
  { label: "Rủi ro thấp", value: "LOW_RISK" },
  { label: "Không kết luận", value: "INCONCLUSIVE" },
  { label: "Rủi ro cao", value: "HIGH_RISK" },
]

const SORT_OPTIONS: Array<{ label: string; value: SortKey }> = [
  { label: "Uy tín cao nhất", value: "reputation" },
  { label: "Giá tăng dần", value: "price_asc" },
  { label: "Giá giảm dần", value: "price_desc" },
  { label: "Khối lượng còn nhiều", value: "quantity" },
]

const LISTINGS: Listing[] = [
  {
    id: "L001",
    productName: "Robusta sàng 18",
    province: "Ea Kar, Đắk Lắk",
    sellerName: "HTX Nông nghiệp Ea Kar",
    sellerReputation: 88,
    commodity: "COFFEE",
    remainingTons: 40,
    pricePerKg: 118500,
    geoVerificationStatus: "CHECKED",
    geoRiskLevel: "LOW_RISK",
  },
  {
    id: "L002",
    productName: "Arabica Cầu Đất",
    province: "Lạc Dương, Lâm Đồng",
    sellerName: "HTX Cà phê Cầu Đất",
    sellerReputation: 81,
    commodity: "COFFEE",
    remainingTons: 12,
    pricePerKg: 195000,
    geoVerificationStatus: "CHECKED",
    geoRiskLevel: "HIGH_RISK",
  },
  {
    id: "L003",
    productName: "Mủ nước SVR 3L",
    province: "Dầu Tiếng, Tây Ninh",
    sellerName: "HTX Cao su Dầu Tiếng",
    sellerReputation: 84,
    commodity: "RUBBER",
    remainingTons: 60,
    pricePerKg: 39200,
    geoVerificationStatus: "CHECKED",
    geoRiskLevel: "INCONCLUSIVE",
  },
  {
    id: "L004",
    productName: "Gạo ST25",
    province: "Ngã Năm, Sóc Trăng",
    sellerName: "HTX Lúa gạo Ngã Năm",
    sellerReputation: 90,
    commodity: "RICE",
    remainingTons: 80,
    pricePerKg: 21500,
  },
  {
    id: "L005",
    productName: "Điều thô W320",
    province: "Bù Đăng, Bình Phước",
    sellerName: "HTX Điều Bù Đăng",
    sellerReputation: 86,
    commodity: "CASHEW",
    remainingTons: 25,
    pricePerKg: 31000,
  },
  {
    id: "L006",
    productName: "Robusta honey",
    province: "Krông Pắc, Đắk Lắk",
    sellerName: "HTX Krông Pắc",
    sellerReputation: 79,
    commodity: "COFFEE",
    remainingTons: 18,
    pricePerKg: 121000,
    geoVerificationStatus: "PENDING",
  },
  {
    id: "L007",
    productName: "Cao su RSS3",
    province: "Phú Riềng, Bình Phước",
    sellerName: "HTX Cao su Phú Riềng",
    sellerReputation: 91,
    commodity: "RUBBER",
    remainingTons: 35,
    pricePerKg: 41500,
    geoVerificationStatus: "CHECKED",
    geoRiskLevel: "LOW_RISK",
  },
  {
    id: "L008",
    productName: "Gạo OM 18",
    province: "Thoại Sơn, An Giang",
    sellerName: "HTX Nông nghiệp Thoại Sơn",
    sellerReputation: 83,
    commodity: "RICE",
    remainingTons: 120,
    pricePerKg: 18200,
  },
  {
    id: "L009",
    productName: "Điều nhân W240",
    province: "Đồng Phú, Bình Phước",
    sellerName: "HTX Điều Đồng Phú",
    sellerReputation: 87,
    commodity: "CASHEW",
    remainingTons: 15,
    pricePerKg: 78000,
  },
  {
    id: "L010",
    productName: "Robusta Cầu Đất",
    province: "Đà Lạt, Lâm Đồng",
    sellerName: "Công ty TNHH Cà phê Đà Lạt",
    sellerReputation: 93,
    commodity: "COFFEE",
    remainingTons: 55,
    pricePerKg: 132000,
    geoVerificationStatus: "CHECKED",
    geoRiskLevel: "LOW_RISK",
  },
  {
    id: "L011",
    productName: "Gạo IR 50404",
    province: "Châu Phú, An Giang",
    sellerName: "HTX Lúa gạo Châu Phú",
    sellerReputation: 80,
    commodity: "RICE",
    remainingTons: 200,
    pricePerKg: 15800,
  },
  {
    id: "L012",
    productName: "Mủ tờ SVR 10",
    province: "Bù Đốp, Bình Phước",
    sellerName: "HTX Cao su Bù Đốp",
    sellerReputation: 82,
    commodity: "RUBBER",
    remainingTons: 28,
    pricePerKg: 37800,
    geoVerificationStatus: "CHECKED",
    geoRiskLevel: "INCONCLUSIVE",
  },
]

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatPrice(n: number) {
  return n.toLocaleString("vi-VN") + " ₫/kg"
}

function formatQuantity(tons: number) {
  return tons + " tấn"
}

function reputationVariant(score: number): "success" | "warning" | "danger" {
  if (score >= 80) return "success"
  if (score >= 60) return "warning"
  return "danger"
}

// ─── LoginBanner ──────────────────────────────────────────────────────────────

function LoginBanner() {
  return (
    <div className="flex items-center gap-2.5 rounded-[10px] border-[0.5px] border-[#BBF7D0] bg-[#F6FDF9] px-4 py-3">
      <span className="ms text-[19px] text-primary">info</span>
      <span className="flex-1 text-[13.5px] text-text-secondary">
        Đăng nhập bằng tài khoản doanh nghiệp đã duyệt để gửi đề nghị hợp
        đồng và nạp ký quỹ.
      </span>
      <Link
        to="/login"
        id="marketplace-login-cta"
        className={cn(
          buttonVariants({ size: "nav" }),
          "text-[13px]"
        )}
      >
        Đăng nhập
      </Link>
    </div>
  )
}

// ─── FilterBar ────────────────────────────────────────────────────────────────

interface FilterBarProps {
  search: string
  onSearch: (v: string) => void
  commodity: Commodity | ""
  onCommodity: (v: Commodity | "") => void
  province: string
  onProvince: (v: string) => void
  minReputation: number
  onMinReputation: (v: number) => void
  eudrRisk: GeoRiskLevel | ""
  onEudrRisk: (v: GeoRiskLevel | "") => void
}

function FilterBar({
  search,
  onSearch,
  commodity,
  onCommodity,
  province,
  onProvince,
  minReputation,
  onMinReputation,
  eudrRisk,
  onEudrRisk,
}: FilterBarProps) {
  return (
    <div className="flex flex-wrap items-center gap-2.5 rounded-[12px] border-[0.5px] border-border bg-surface px-4 py-3.5">
      {/* Search */}
      <label
        style={{ borderRadius: "8px" }}
        className="flex flex-1 min-w-[200px] items-center gap-2 border-[0.5px] border-border px-3 h-[38px] text-sm text-text-muted"
      >
        <span className="ms text-[18px]">search</span>
        <input
          id="marketplace-search"
          type="text"
          value={search}
          onChange={(e) => onSearch(e.target.value)}
          placeholder="Tìm theo tên bên bán, giống, vùng..."
          className="flex-1 bg-transparent text-text-primary placeholder:text-text-muted outline-none text-sm"
        />
      </label>

      {/* Commodity */}
      <select
        id="marketplace-filter-commodity"
        value={commodity}
        onChange={(e) => onCommodity(e.target.value as Commodity | "")}
        style={{ borderRadius: "8px" }}
        className="flex h-[38px] items-center border-[0.5px] border-border bg-surface px-3 text-sm text-text-primary outline-none cursor-pointer"
      >
        <option value="">Tất cả mặt hàng</option>
        {(Object.keys(COMMODITY_LABEL) as Commodity[]).map((c) => (
          <option key={c} value={c}>
            {COMMODITY_LABEL[c]}
          </option>
        ))}
      </select>

      {/* Province */}
      <select
        id="marketplace-filter-province"
        value={province}
        onChange={(e) => onProvince(e.target.value)}
        style={{ borderRadius: "8px" }}
        className="flex h-[38px] items-center border-[0.5px] border-border bg-surface px-3 text-sm text-text-secondary outline-none cursor-pointer"
      >
        <option value="">Tỉnh: Tất cả</option>
        {PROVINCE_OPTIONS.map((p) => (
          <option key={p} value={p}>
            {p}
          </option>
        ))}
      </select>

      {/* Reputation */}
      <select
        id="marketplace-filter-reputation"
        value={minReputation}
        onChange={(e) => onMinReputation(Number(e.target.value))}
        style={{ borderRadius: "8px" }}
        className="flex h-[38px] items-center border-[0.5px] border-border bg-surface px-3 text-sm text-text-secondary outline-none cursor-pointer"
      >
        {REPUTATION_OPTIONS.map((r) => (
          <option key={r.value} value={r.value}>
            {r.label}
          </option>
        ))}
      </select>

      {/* EUDR risk — only when commodity is COFFEE or RUBBER or unset */}
      {(commodity === "" || EUDR_COMMODITIES.includes(commodity)) && (
        <select
          id="marketplace-filter-eudr"
          value={eudrRisk}
          onChange={(e) => onEudrRisk(e.target.value as GeoRiskLevel | "")}
          style={{ borderRadius: "8px" }}
          className="flex h-[38px] items-center border-[0.5px] border-border bg-surface px-3 text-sm text-text-secondary outline-none cursor-pointer"
        >
          {EUDR_RISK_OPTIONS.map((r) => (
            <option key={r.value} value={r.value}>
              {r.value === "" ? "Rủi ro EUDR: Tất cả" : r.label}
            </option>
          ))}
        </select>
      )}
    </div>
  )
}

// ─── GeoStatusBadges ──────────────────────────────────────────────────────────

/**
 * Hai badge tách bạch: tiến trình + kết quả — không bao giờ gộp.
 * Xem §3.8 context brief.
 */
function GeoStatusBadges({
  status,
  risk,
}: {
  status: GeoVerificationStatus
  risk?: GeoRiskLevel
}) {
  const statusVariant =
    status === "CHECKED"
      ? "success"
      : status === "PENDING"
        ? "neutral"
        : "warning"

  const riskVariant =
    risk === "LOW_RISK"
      ? "success"
      : risk === "HIGH_RISK"
        ? "danger"
        : "warning"

  return (
    <div className="flex flex-wrap gap-1.5">
      {/* Tiến trình badge — luôn hiện */}
      <StatusBadge variant={statusVariant} className="text-[11.5px] px-2.5 py-1">
        {GEO_VERIFICATION_LABEL[status]}
      </StatusBadge>
      {/* Kết quả badge — chỉ hiện khi CHECKED */}
      {status === "CHECKED" && risk && (
        <StatusBadge variant={riskVariant} className="text-[11.5px] px-2.5 py-1">
          {GEO_RISK_LABEL[risk]}
        </StatusBadge>
      )}
    </div>
  )
}

// ─── ListingCard ──────────────────────────────────────────────────────────────

function ListingCard({ listing }: { listing: Listing }) {
  const isEudr = EUDR_COMMODITIES.includes(listing.commodity)

  return (
    <div
      className="flex flex-col overflow-hidden rounded-[12px] border-[0.5px] border-border bg-surface"
      data-listing-id={listing.id}
    >
      {/* Image placeholder — §5 blocker: ảnh thật chưa có, dùng hatch pattern theo mockup */}
      <div
        className="relative flex h-[120px] items-end p-3"
        style={{
          background:
            "repeating-linear-gradient(135deg,#E4EAE3,#E4EAE3 8px,#EDF1EC 8px,#EDF1EC 16px)",
        }}
        aria-hidden="true"
      >
        <span className="font-mono rounded-[6px] bg-text-primary px-2 py-[3px] text-[11px] text-white">
          {COMMODITY_LABEL[listing.commodity]}
        </span>
      </div>

      {/* Body */}
      <div className="flex flex-1 flex-col px-4 py-3.5">
        {/* Product name + location */}
        <div className="mb-0.5 text-[15px] font-medium">{listing.productName}</div>
        <div className="mb-2.5 text-[12.5px] text-text-secondary">{listing.province}</div>

        {/* Seller row */}
        <div className="flex items-center gap-2 border-t-[0.5px] border-border py-2">
          <div className="size-[26px] rounded-full border-[0.5px] border-border bg-surface-muted" />
          <Link
            to={`/reputation/htx-${listing.id.toLowerCase()}`}
            className="flex-1 text-[12.5px] hover:text-primary hover:underline"
          >
            {listing.sellerName}
          </Link>
          <StatusBadge
            variant={reputationVariant(listing.sellerReputation)}
            className="text-xs px-2 py-0.5"
          >
            {listing.sellerReputation}
          </StatusBadge>
        </div>

        {/* Key stats */}
        <div className="flex justify-between py-1.5 text-[13px]">
          <span className="text-text-secondary">Còn lại</span>
          <span className="font-mono">{formatQuantity(listing.remainingTons)}</span>
        </div>
        <div className="flex justify-between pb-2.5 text-[13px]">
          <span className="text-text-secondary">Giá</span>
          <span className="font-mono">{formatPrice(listing.pricePerKg)}</span>
        </div>

        {/* EUDR badges or non-EUDR note */}
        <div className="mb-3">
          {isEudr && listing.geoVerificationStatus ? (
            <GeoStatusBadges
              status={listing.geoVerificationStatus}
              risk={listing.geoRiskLevel}
            />
          ) : (
            <span className="text-[11.5px] text-text-muted">
              {COMMODITY_LABEL[listing.commodity]} không thuộc EUDR — không cần khai vùng trồng.
            </span>
          )}
        </div>

        {/* CTA */}
        <Link
          to={`/listings/${listing.id}`}
          id={`listing-card-cta-${listing.id}`}
          className="mt-auto block rounded-lg border-[0.5px] border-border py-[9px] text-center text-[13px] font-medium hover:bg-surface-muted transition-colors"
        >
          Xem chi tiết
        </Link>
      </div>
    </div>
  )
}

// ─── EmptyState ───────────────────────────────────────────────────────────────

function EmptyState({ hasFilters }: { hasFilters: boolean }) {
  if (hasFilters) {
    return (
      <div className="col-span-3 flex flex-col items-center gap-3 rounded-[12px] border-[0.5px] border-border bg-surface py-16 text-center">
        <span className="ms text-[40px] text-text-muted">search_off</span>
        <div className="text-[15px] font-medium">Không có nguồn hàng khớp bộ lọc</div>
        <div className="text-sm text-text-secondary">Thử điều chỉnh mặt hàng, tỉnh hoặc mức uy tín.</div>
      </div>
    )
  }
  return (
    <div className="col-span-3 flex flex-col items-center gap-3 rounded-[12px] border-[0.5px] border-border bg-surface py-16 text-center">
      <span className="ms text-[40px] text-text-muted">storefront</span>
      <div className="text-[15px] font-medium">Chưa có nguồn hàng nào</div>
      <div className="text-sm text-text-secondary">Bên bán đã được duyệt sẽ đăng listing tại đây.</div>
    </div>
  )
}

// ─── MarketplacePage ──────────────────────────────────────────────────────────

function MarketplacePage() {
  const [search, setSearch] = useState("")
  const [commodity, setCommodity] = useState<Commodity | "">("")
  const [province, setProvince] = useState("")
  const [minReputation, setMinReputation] = useState(0)
  const [eudrRisk, setEudrRisk] = useState<GeoRiskLevel | "">("")
  const [sortKey, setSortKey] = useState<SortKey>("reputation")
  const [page, setPage] = useState(1)

  // Reset page khi filter/sort thay đổi
  function withReset<T>(setter: (v: T) => void) {
    return (v: T) => { setter(v); setPage(1) }
  }

  // Filter
  const filtered = LISTINGS.filter((l) => {
    if (
      search &&
      !l.productName.toLowerCase().includes(search.toLowerCase()) &&
      !l.sellerName.toLowerCase().includes(search.toLowerCase()) &&
      !l.province.toLowerCase().includes(search.toLowerCase())
    )
      return false
    if (commodity && l.commodity !== commodity) return false
    if (province && !l.province.includes(province)) return false
    if (minReputation && l.sellerReputation < minReputation) return false
    if (eudrRisk) {
      if (!EUDR_COMMODITIES.includes(l.commodity)) return false
      if (l.geoRiskLevel !== eudrRisk) return false
    }
    return true
  })

  // Sort
  const sorted = [...filtered].sort((a, b) => {
    switch (sortKey) {
      case "price_asc":
        return a.pricePerKg - b.pricePerKg
      case "price_desc":
        return b.pricePerKg - a.pricePerKg
      case "quantity":
        return b.remainingTons - a.remainingTons
      default:
        return b.sellerReputation - a.sellerReputation
    }
  })

  const hasFilters =
    !!search || !!commodity || !!province || !!minReputation || !!eudrRisk

  const currentSortLabel =
    SORT_OPTIONS.find((s) => s.value === sortKey)?.label ?? ""

  // Pagination
  const PAGE_SIZE = 6
  const totalPages = Math.ceil(sorted.length / PAGE_SIZE)
  const paginated = sorted.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)

  return (
    <div className="min-h-screen bg-page-bg text-text-primary">
      <title>Sàn nguồn hàng — AgriContract</title>
      <PublicNav />

      <div className="mx-auto max-w-[1280px] px-10 pt-8 pb-20">
        {/* Page header */}
        <h1 className="mb-1.5 text-[26px] font-medium">Sàn nguồn hàng</h1>
        <p className="mb-2 max-w-[720px] text-sm text-text-secondary">
          Listing công khai từ bên bán đã được duyệt. Xem uy tín và mức rủi ro
          EUDR trước khi liên hệ.
        </p>

        {/* Login banner */}
        <div className="my-3.5">
          <LoginBanner />
        </div>

        {/* Filter bar */}
        <FilterBar
          search={search}
          onSearch={withReset(setSearch)}
          commodity={commodity}
          onCommodity={withReset(setCommodity)}
          province={province}
          onProvince={withReset(setProvince)}
          minReputation={minReputation}
          onMinReputation={withReset(setMinReputation)}
          eudrRisk={eudrRisk}
          onEudrRisk={withReset(setEudrRisk)}
        />

        {/* Result meta */}
        <div className="mt-2 mb-[18px] text-[13px] text-text-secondary">
          {sorted.length} nguồn hàng · sắp xếp theo{" "}
          <select
            id="marketplace-sort"
            value={sortKey}
            onChange={(e) => setSortKey(e.target.value as SortKey)}
            className="bg-transparent text-text-primary font-medium cursor-pointer outline-none"
            aria-label="Sắp xếp theo"
          >
            {SORT_OPTIONS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>
          {/* sr-only fallback label for screen readers */}
          <span className="sr-only">{currentSortLabel}</span>
        </div>

        {/* Grid */}
        <div className="grid grid-cols-3 gap-4 max-[1023px]:grid-cols-2 max-[639px]:grid-cols-1">
          {sorted.length === 0 ? (
            <EmptyState hasFilters={hasFilters} />
          ) : (
            paginated.map((listing) => (
              <ListingCard key={listing.id} listing={listing} />
            ))
          )}
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="mt-8">
            <Pagination
              page={page}
              totalPages={totalPages}
              onPageChange={setPage}
            />
          </div>
        )}

        {/* Disclaimer */}
        <p className="mt-5 text-[12.5px] text-text-muted">
          Giá hiển thị là giá chào bán tham khảo, không phải giá giao dịch cuối
          trên nền tảng.
        </p>
      </div>

      <Footer />
    </div>
  )
}

export { MarketplacePage }
