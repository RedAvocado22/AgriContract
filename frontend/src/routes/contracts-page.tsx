import { useState } from "react"
import { Link } from "react-router-dom"
import { useAuthStore } from "@/shared/auth/auth-store"
import { useContracts } from "@/features/contracts/hooks/use-contracts"
import { ContractsLayout } from "@/features/contracts/components/contracts-layout"
import {
  ContractBadge,
  ContractFailure,
  ContractLoading,
} from "@/features/contracts/components/contract-state"
import {
  filterStatuses,
  statusLabels,
  decimalMoney,
} from "@/features/contracts/types/presentation"
import type {
  Contract,
  ParticipantRole,
} from "@/features/contracts/types/contract"
import { SelectDropdown } from "@/shared/ui/select-dropdown"
import { EmptyState } from "@/shared/ui/empty-state"
import { Pagination } from "@/shared/ui/pagination"
import { DataTable } from "@/shared/ui/data-table"
import { RecordCard } from "@/shared/ui/record-card"
import { MoneyDisplay } from "@/shared/ui/money-display"

export function ContractsPage() {
  const { roles } = useAuthStore()
  const [role, setRole] = useState<ParticipantRole>(
    roles.includes("BUYER") ? "BUYER" : "SELLER"
  )
  const [status, setStatus] = useState("")
  const [page, setPage] = useState(0)
  const query = useContracts(role, status, page)
  const partner = (item: Contract) =>
    (role === "BUYER" ? item.sellerName : item.buyerName) ??
    "Chưa có tên đối tác"
  const money = (item: Contract) => {
    const total = decimalMoney(item.terms.totalContractValue)
    return total === null ? (
      "Chưa được cung cấp"
    ) : (
      <MoneyDisplay amount={total} className="break-all text-sm" />
    )
  }
  const open = (item: Contract) => (
    <Link
      className="break-all text-primary underline"
      to={`/contracts/${encodeURIComponent(item.id)}`}
    >
      {item.id}
    </Link>
  )
  return (
    <ContractsLayout title="Hợp đồng của tôi">
      <p className="text-sm text-text-secondary">
        Theo dõi đề nghị, điều khoản và trạng thái kích hoạt của các hợp đồng
        bạn tham gia.
      </p>
      <div className="grid gap-3 sm:grid-cols-2">
        <SelectDropdown
          id="contract-role"
          label="Vai trò của bạn"
          value={role}
          options={roles
            .filter((r) => r === "BUYER" || r === "SELLER")
            .map((r) => ({
              value: r,
              label: r === "BUYER" ? "Bên mua" : "Bên bán",
            }))}
          onChange={(v) => {
            if (v === "BUYER" || v === "SELLER") {
              setRole(v)
              setPage(0)
            }
          }}
        />
        <SelectDropdown
          id="contract-status"
          label="Trạng thái"
          value={status || "ALL"}
          options={[
            { value: "ALL", label: "Tất cả trạng thái" },
            ...filterStatuses.map((value) => ({
              value,
              label: statusLabels[value],
            })),
          ]}
          onChange={(v) => {
            setStatus(v === "ALL" ? "" : v ?? "")
            setPage(0)
          }}
        />
      </div>
      {query.isPending ? (
        <ContractLoading />
      ) : query.isError ? (
        <ContractFailure
          error={query.error}
          retry={() => void query.refetch()}
        />
      ) : !query.data?.content.length ? (
        <EmptyState
          title="Chưa có hợp đồng"
          description="Không có hợp đồng phù hợp với vai trò và bộ lọc hiện tại."
        />
      ) : (
        <>
          <DataTable
            className="hidden lg:block"
            rowKey={(item) => item.id}
            rows={query.data.content}
            columns={[
              { key: "id", header: "Mã hợp đồng", render: open },
              {
                key: "partner",
                header: "Đối tác",
                render: (item) => (
                  <span className="break-words">{partner(item)}</span>
                ),
              },
              { key: "money", header: "Tổng giá trị", render: money },
              {
                key: "status",
                header: "Trạng thái",
                render: (item) => <ContractBadge status={item.status} />,
              },
            ]}
          />
          <div className="space-y-3 lg:hidden">
            {query.data.content.map((item) => (
              <RecordCard
                key={item.id}
                code={open(item)}
                badge={<ContractBadge status={item.status} />}
                subtitle={partner(item)}
                highlight={money(item)}
              />
            ))}
          </div>
        </>
      )}
      {query.data && !query.isError && query.data.totalPages > 0 && (
        <Pagination
          page={page + 1}
          totalPages={query.data.totalPages}
          onPageChange={(p) => setPage(p - 1)}
        />
      )}
      <p className="text-xs text-text-muted">
        Mã và thông tin hiển thị từ máy chủ. Thời gian tạo và tổng giá trị của
        bản ghi cũ chưa được cung cấp.
      </p>
    </ContractsLayout>
  )
}
