import { Link } from 'react-router-dom'

import { useAuthStore } from '../stores/authStore'

export function DashboardPage() {
  const user = useAuthStore((state) => state.user)

  return (
    <div className="dashboard-page">
      <section className="page-title-row">
        <div>
          <h1>Tổng quan</h1>
          <p>Xin chào {user?.name ?? 'bạn'}, theo dõi nhanh hoạt động giao dịch hôm nay.</p>
        </div>

        <Link className="primary-button" to="/listings">
          <span className="material-symbols-outlined">list_alt</span>
          Xem listings
        </Link>
      </section>

      <section className="stat-grid">
        <article className="stat-card">
          <span>Listings đang mở</span>
          <strong>12</strong>
          <small>+3 trong tuần này</small>
        </article>
        <article className="stat-card">
          <span>Hợp đồng chờ ký</span>
          <strong>4</strong>
          <small>Cần phản hồi trong 24h</small>
        </article>
        <article className="stat-card">
          <span>Tiền trong Escrow</span>
          <strong>1.500.000.000 VND</strong>
          <small>Đã khóa an toàn</small>
        </article>
      </section>

      <section className="panel-card">
        <h3>Hoạt động gần đây</h3>
        <div className="activity-list">
          <div>
            <span className="material-symbols-outlined">lock</span>
            <p>Escrow cho hợp đồng cà phê Robusta đã được khóa.</p>
            <small>2 giờ trước</small>
          </div>
          <div>
            <span className="material-symbols-outlined">description</span>
            <p>Bên mua đã gửi đề nghị mới cho lô gạo ST25.</p>
            <small>Hôm nay</small>
          </div>
          <div>
            <span className="material-symbols-outlined">payments</span>
            <p>Thanh toán đợt 1 đã được giải ngân từ Escrow.</p>
            <small>Hôm qua</small>
          </div>
        </div>
      </section>
    </div>
  )
}
