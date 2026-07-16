import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { z } from 'zod'

import { authApi } from '../api/authApi'
import { AuthHeader } from '../components/layout/AuthHeader'
import { env } from '../config/env'
import { useAuthStore } from '../stores/authStore'
import type { UserRole } from '../types/auth'

const loginSchema = z
  .object({
    identifier: z.string(),
    password: z.string(),
    role: z.enum(['BUYER', 'SELLER', 'ADMIN']).optional(),
  })
  .superRefine((values, context) => {
    if (!env.useMocks) return
    if (!values.identifier.trim()) {
      context.addIssue({ code: 'custom', path: ['identifier'], message: 'Nhập email hoặc tên đăng nhập' })
    }
    if (!values.password) {
      context.addIssue({ code: 'custom', path: ['password'], message: 'Nhập mật khẩu' })
    }
  })

type LoginFormValues = z.infer<typeof loginSchema>

export function LoginPage() {
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)
  const loginWithMock = useAuthStore((state) => state.loginWithMock)
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      identifier: '',
      password: '',
      role: 'BUYER',
    },
  })

  const loginMutation = useMutation({
    mutationFn: authApi.login,
    onSuccess: async (result, values) => {
      if (env.useMocks && result) {
        loginWithMock({
          email: values.identifier.includes('@') ? values.identifier : `${values.identifier}@example.com`,
          role: (values.role ?? 'BUYER') as UserRole,
        })
        navigate('/dashboard')
      }
    },
  })

  if (user) {
    return <Navigate to="/dashboard" replace />
  }

  const onSubmit = (values: LoginFormValues) => {
    loginMutation.mutate(values)
  }

  return (
    <>
      <AuthHeader />
      <div className="shell-grid auth-shell">
        <aside className="sidebar">
          <div className="sidebar__group">
          <nav className="auth-rail__nav" aria-label="Điều hướng công khai">
            <Link className="sidebar-link" to="/listings">
              <span className="material-symbols-outlined">list_alt</span>
              Tin hàng
            </Link>
          </nav>
          </div>
        </aside>

        <main className="shell-content auth-main">
        <div className="auth-card">
          <div className="auth-card__hero">
            <div className="brand-brandmark">eco</div>
            <h1>AgriContract</h1>
            <p>Giao dịch nông sản có hợp đồng và ký quỹ bảo chứng.</p>
          </div>

          <form className="auth-form" onSubmit={handleSubmit(onSubmit)}>
            {env.useMocks ? (
              <>
                <label>
                  <span>Email hoặc tên đăng nhập</span>
                  <input {...register('identifier')} type="text" autoComplete="username" />
                  {errors.identifier ? <small>{errors.identifier.message}</small> : null}
                </label>
                <label>
                  <span>Mật khẩu</span>
                  <input {...register('password')} type="password" />
                  {errors.password ? <small>{errors.password.message}</small> : null}
                </label>
                <label>
                  <span>Vai trò giả lập</span>
                  <select {...register('role')}>
                    <option value="BUYER">Bên mua</option>
                    <option value="SELLER">Bên bán</option>
                    <option value="ADMIN">Quản trị</option>
                  </select>
                </label>
              </>
            ) : null}

            {loginMutation.isError ? <small>Email hoặc mật khẩu không đúng.</small> : null}

            <button
              className="primary-button primary-button--full"
              type="submit"
              disabled={loginMutation.isPending}
            >
              {loginMutation.isPending ? 'Đang chuyển hướng...' : env.useMocks ? 'Đăng nhập' : 'Đăng nhập với Keycloak'}
              <span className="material-symbols-outlined">arrow_forward</span>
            </button>
          </form>

          <div className="auth-trust">
            <div>
              <span className="material-symbols-outlined">verified_user</span>
              <span>Hồ sơ xác minh</span>
            </div>
            <div>
              <span className="material-symbols-outlined">lock</span>
              <span>Bảo chứng ký quỹ</span>
            </div>
            <div>
              <span className="material-symbols-outlined">gavel</span>
              <span>Quy trình tranh chấp</span>
            </div>
          </div>
        </div>
        </main>
      </div>
    </>
  )
}
