import { Link } from 'react-router-dom'

import { useContractsQuery } from '../hooks/useContractsQuery'
import { useListingsQuery } from '../hooks/useListingsQuery'
import { useAuthStore } from '../stores/authStore'
import { formatMoney } from '../utils/formatters'

const listingFilters = {
  categories: [],
  deliveryWindow: 'all',
  search: '',
  sortBy: 'latest' as const,
}

export function DashboardPage() {
  const user = useAuthStore((state) => state.user)
  const role = user?.role?.trim().toUpperCase()
  const { data: listings } = useListingsQuery(listingFilters)
  const { data: contracts } = useContractsQuery(role === 'SELLER' ? 'SELLER' : 'BUYER')
  const openListings = listings?.filter((listing) => listing.status === 'ACTIVE').length ?? 0
  const pendingContracts =
    contracts?.filter((contract) => ['OFFERED', 'NEGOTIATING', 'SIGNED'].includes(contract.status)).length ?? 0
  const escrowEstimate =
    contracts?.reduce(
      (total, contract) =>
        contract.status === 'ACTIVE' || contract.status === 'SIGNED'
          ? total + contract.terms.quantity.value * contract.terms.agreedPrice.amount
          : total,
      0,
    ) ?? 0

  return (
    <div className="dashboard-page">
      <section className="page-title-row">
        <div>
          <h1>Overview</h1>
          <p>Hello {user?.name ?? 'there'}, here is the fastest read on today&apos;s trading work.</p>
        </div>

        <Link className="primary-button" to="/listings">
          <span className="material-symbols-outlined">list_alt</span>
          Browse listings
        </Link>
      </section>

      <section className="stat-grid">
        <article className="stat-card">
          <span>Open listings</span>
          <strong>{openListings}</strong>
          <small>Available for contract offers</small>
        </article>
        <article className="stat-card">
          <span>Contracts needing attention</span>
          <strong>{pendingContracts}</strong>
          <small>Offers, signatures, or deposits</small>
        </article>
        <article className="stat-card">
          <span>Estimated escrow exposure</span>
          <strong>{formatMoney(escrowEstimate)}</strong>
          <small>Based on signed and active contracts</small>
        </article>
      </section>

      <section className="panel-card">
        <h3>Workflow</h3>
        <div className="activity-list">
          <div>
            <span className="material-symbols-outlined">storefront</span>
            <p>Review active supply and create a contract offer from a listing detail page.</p>
            <small>Marketplace</small>
          </div>
          <div>
            <span className="material-symbols-outlined">description</span>
            <p>Sign, cancel, confirm delivery, or raise a dispute from the contracts screen.</p>
            <small>Contracts</small>
          </div>
          <div>
            <span className="material-symbols-outlined">account_balance_wallet</span>
            <p>Track locked funds and seller deposits in escrow by contract.</p>
            <small>Escrow</small>
          </div>
        </div>
      </section>
    </div>
  )
}
