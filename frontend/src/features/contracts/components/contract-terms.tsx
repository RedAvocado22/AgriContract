import { KeyValueList } from "@/shared/ui/key-value-list"
import { MoneyDisplay } from "@/shared/ui/money-display"
import { decimalMoney, vietnamTime } from "../types/presentation"
import type { Contract } from "../types/contract"

const labels: Record<string, string> = {
  agreedPrice: "Đơn giá (VND/kg)",
  totalContractValue: "Tổng giá trị (VND)",
  currency: "Tiền tệ",
  milestoneSchedule: "Lịch giao hàng cam kết",
  milestoneIndex: "Đợt giao",
  committedQuantity: "Khối lượng cam kết (kg)",
  expectedDeliveryDate: "Ngày giao dự kiến",
  graceDays: "Số ngày làm việc gia hạn",
  qualityTerms: "Điều kiện chất lượng",
  committedSpec: "Chất lượng cam kết",
  deviationPolicy: "Chính sách sai lệch",
  commodity: "Hàng hóa",
  type: "Loại cà phê",
  moisturePercent: "Độ ẩm (%)",
  foreignMatterPercent: "Tạp chất (%)",
  blackBrokenBeansPercent: "Hạt đen vỡ (%)",
  direction: "Chiều đánh giá",
  tolerance: "Ngưỡng dung sai",
  penalty: "Ngưỡng điều chỉnh",
  reject: "Ngưỡng từ chối",
  discountRate: "Tỷ lệ giảm giá",
  rejectOnMismatch: "Từ chối nếu không khớp",
  deliveryTerms: "Điều khoản giao nhận",
  deliveryResponsibility: "Trách nhiệm giao hàng",
  riskTransferPoint: "Thời điểm chuyển rủi ro",
  titleTransferPoint: "Thời điểm chuyển quyền sở hữu",
  transportCostBearer: "Bên chịu phí vận chuyển",
  unloadingCostBearer: "Bên chịu phí dỡ hàng",
  inspectionTerms: "Điều khoản kiểm định",
  requirement: "Yêu cầu kiểm định",
  requiredTier: "Cấp kiểm định",
  reportDueBusinessDays: "Hạn báo cáo (ngày làm việc)",
  inspectionCostResponsibility: "Trách nhiệm phí kiểm định",
  priceBand: "Biên độ giá",
  minimumUnitPrice: "Giá thấp nhất (VND/kg)",
  maximumUnitPrice: "Giá cao nhất (VND/kg)",
  priceAdjustmentRule: "Quy tắc điều chỉnh giá",
  acceptanceMode: "Cơ chế chấp thuận",
  cutoff: "Hạn chấp thuận",
  maximumAcceptedAdjustmentsPerMilestone: "Số lần điều chỉnh mỗi đợt",
  toleranceRate: "Tỷ lệ dung sai",
  shortfallPenaltyThreshold: "Ngưỡng thiếu hụt",
  buyerPenaltyRate: "Tỷ lệ phạt bên mua",
  sellerPenaltyRate: "Tỷ lệ phạt bên bán",
  forceMajeureReportWindowDays: "Hạn báo bất khả kháng (ngày)",
  buyerConfirmWindowDays: "Hạn xác nhận (ngày làm việc)",
  buyerDepositRate: "Tỷ lệ cọc bên mua",
  sellerDepositRate: "Tỷ lệ cọc bên bán",
  disputeFloorReleaseRate: "Tỷ lệ giải ngân sàn",
  level2InspectorOrg: "Tổ chức giám định cấp 2",
  legalProfile: "Điều kiện pháp lý",
  governingLaw: "Luật áp dụng",
  contractType: "Loại hợp đồng",
  maxContractualPenaltyRate: "Tỷ lệ phạt tối đa",
  damagesPolicy: "Chính sách bồi thường",
  goodsTerms: "Hàng hóa theo hồ sơ đã chốt",
  productName: "Tên hàng hóa",
  listingId: "Mã nguồn hàng",
  productId: "Mã sản phẩm",
  categoryId: "Mã danh mục",
  categoryName: "Danh mục",
  unitOfMeasure: "Đơn vị",
  cropYear: "Niên vụ",
  harvestSeason: "Mùa thu hoạch",
  originSnapshot: "Nguồn gốc",
  regionText: "Khu vực",
  plotRefs: "Thửa đất",
  registryEntryId: "Mã đăng ký",
  householdLabel: "Hộ sản xuất",
  geometryType: "Loại hình học",
  geometryHash: "Hash hình học",
  verificationLevel: "Mức xác minh",
  traceabilitySnapshot: "Truy xuất nguồn gốc",
  evidencePackageId: "Mã bộ chứng cứ",
  packageHash: "Hash bộ chứng cứ",
  contractTemplateCode: "Mã mẫu hợp đồng",
  contractTemplateVersion: "Phiên bản mẫu",
  contractTemplateHash: "Hash mẫu",
  coffeeQualityProfileVersion: "Phiên bản hồ sơ chất lượng",
  quantity: "Khối lượng",
  value: "Giá trị",
  amount: "Số tiền",
  unit: "Đơn vị",
  deliveryDeadline: "Hạn giao hàng",
  qualitySpec: "Mô tả chất lượng",
  milestoneId: "Mã đợt giao",
  status: "Trạng thái",
  fundingStatus: "Trạng thái tài trợ",
  batchAmount: "Giá trị đợt giao",
  effectiveUnitPrice: "Đơn giá hiệu lực",
  acceptedPriceAdjustment: "Điều chỉnh đã chấp thuận",
  fundingDelayBusinessDays: "Ngày làm việc chậm tài trợ",
  effectiveDeliveryDeadline: "Hạn giao hiệu lực",
}
const values: Record<string, string> = {
  COFFEE: "Cà phê",
  VND: "Đồng Việt Nam",
  KG: "kg",
  ARABICA: "Arabica",
  ROBUSTA: "Robusta",
  BUYER: "Bên mua",
  SELLER: "Bên bán",
  SHARED: "Hai bên chia sẻ",
  MAXIMUM: "Tối đa",
  MINIMUM: "Tối thiểu",
  SELLER_DELIVERS: "Bên bán giao hàng",
  BUYER_COLLECTS: "Bên mua nhận tại nguồn",
  SELLER_DISPATCH: "Khi bên bán xuất hàng",
  BUYER_RECEIPT: "Khi bên mua nhận hàng",
  BUYER_ACCEPTANCE: "Khi bên mua chấp nhận",
  FULL_PAYMENT: "Khi thanh toán đủ",
  NONE_UNLESS_DISPUTED: "Chỉ kiểm định khi có tranh chấp",
  MANDATORY_BEFORE_SETTLEMENT: "Bắt buộc trước tất toán",
  LEVEL_1_5: "Cấp 1.5",
  LEVEL_2: "Cấp 2",
  LOSER_PAYS: "Bên chịu trách nhiệm theo kết luận",
  BOTH_ACCEPT: "Hai bên chấp thuận",
  BEFORE_FUNDING_REQUEST: "Trước yêu cầu tài trợ",
  VN_COMMERCIAL_LAW: "Luật Thương mại Việt Nam",
  VN_CIVIL_LAW: "Pháp luật dân sự Việt Nam",
  COMMERCIAL_CUMULATIVE_IF_PROVEN: "Bồi thường thiệt hại khi có chứng minh",
  CIVIL_PENALTY_ONLY_UNLESS_EXPRESSLY_CUMULATIVE:
    "Phạt theo thỏa thuận; bồi thường khi có thỏa thuận cộng dồn",
  EXPRESS_PENALTY_ONLY: "Chỉ áp dụng phạt theo thỏa thuận",
  SELF_DECLARED: "Tự khai báo",
  CADASTRAL_BACKED: "Có hồ sơ địa chính",
  POINT: "Điểm",
  POLYGON: "Đa giác",
  PENDING: "Đang chờ",
  READY: "Sẵn sàng",
  FAILED: "Thất bại",
  FUNDING_PENDING: "Chờ tài trợ",
  FUNDING_FAILED: "Tài trợ thất bại",
  LOCKED: "Đã khóa",
  CREATED: "Đã tạo",
}
function Value({ field, value }: { field: string; value: unknown }) {
  if (value == null) return <span>Chưa có thông tin</span>
  if (Array.isArray(value))
    return value.length ? (
      <div className="space-y-3">
        {value.map((item, i) => (
          <div key={i} className="border-l-2 border-border pl-3">
            <Value field={field} value={item} />
          </div>
        ))}
      </div>
    ) : (
      <span>Chưa có dữ liệu</span>
    )
  if (typeof value === "object")
    return (
      <div className="space-y-2">
        {Object.entries(value).map(([key, item]) => (
          <div key={key} className="min-w-0">
            <span className="text-xs text-text-secondary">
              {labels[key] ?? `Trường chưa có nhãn (${key})`}
            </span>
            <div className="break-words text-sm [overflow-wrap:anywhere]">
              <Value field={key} value={item} />
            </div>
          </div>
        ))}
      </div>
    )
  if (
    [
      "agreedPrice",
      "totalContractValue",
      "minimumUnitPrice",
      "maximumUnitPrice",
      "batchAmount",
      "effectiveUnitPrice",
      "amount",
    ].includes(field)
  ) {
    const money = decimalMoney(value)
    return money === null ? (
      <span>Chưa xác định chính xác</span>
    ) : (
      <MoneyDisplay className="text-sm" amount={money} />
    )
  }
  if (typeof value === "boolean") return <span>{value ? "Có" : "Không"}</span>
  if (typeof value === "string" && /Date|Deadline$/.test(field))
    return <span>{vietnamTime(value)}</span>
  return <span>{values[String(value)] ?? String(value)}</span>
}
export function ContractTerms({ contract }: { contract: Contract }) {
  return (
    <section className="space-y-4" aria-label="Điều khoản hợp đồng">
      <h2 className="text-lg">Điều khoản hiện hành</h2>
      <KeyValueList
        className="[&>div]:flex-col [&>div]:items-start [&>div]:gap-2 sm:[&>div]:flex-row [&>div>span]:min-w-0 [&>div>span]:break-all"
        items={[
          { label: "Bên mua", value: contract.buyerName ?? contract.buyerId },
          { label: "Bên bán", value: contract.sellerName ?? contract.sellerId },
          { label: "Mã nguồn hàng", value: contract.listingId },
          {
            label: "Khối lượng đã giữ (kg)",
            value: contract.reservedQuantity ?? "Chưa được cung cấp",
          },
        ]}
      />
      <p className="text-xs text-text-secondary">
        Khi chưa có tên doanh nghiệp, hiển thị mã bên tham gia do máy chủ cung
        cấp. Các tỷ lệ giữ nguyên giá trị thập phân của điều khoản; số tiền cọc
        chờ máy chủ cung cấp.
      </p>
      <div className="grid gap-4 sm:grid-cols-2">
        {Object.entries(contract.terms).map(([key, value]) => (
          <section
            key={key}
            className="min-w-0 rounded-md border border-border bg-surface p-4"
          >
            <h3 className="mb-3 text-sm text-text-secondary">
              {labels[key] ?? `Trường chưa có nhãn (${key})`}
            </h3>
            <Value field={key} value={value} />
          </section>
        ))}
      </div>
      <h2 className="text-lg">Các đợt thực hiện</h2>
      {contract.milestones.length ? (
        <Value field="milestones" value={contract.milestones} />
      ) : (
        <p className="text-sm text-text-secondary">
          Chưa có dữ liệu đợt thực hiện từ API. Lịch giao cam kết (nếu có) được
          trình bày trong điều khoản phía trên.
        </p>
      )}
    </section>
  )
}
