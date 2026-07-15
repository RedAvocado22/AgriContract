import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { z } from 'zod'

import { authApi, TOKEN_STORAGE_KEY } from '../api/authApi'
import { isNotFoundError, userApi } from '../api/userApi'
import { AuthHeader } from '../components/layout/AuthHeader'
import { env } from '../config/env'
import { useAuthStore } from '../stores/authStore'
import type { UserRole } from '../types/auth'

const loginSchema = z.object({
  identifier: z.string().trim().min(1, 'Nhập email hoặc tên đăng nhập'),
  password: z.string().nonempty('Nhập mật khẩu'),
  role: z.enum(['BUYER', 'SELLER', 'ADMIN']).optional(),
})

type LoginFormValues = z.infer<typeof loginSchema>

export function LoginPage() {
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)
  const setSession = useAuthStore((state) => state.setSession)
  const setUser = useAuthStore((state) => state.setUser)
  const loginWithMock = useAuthStore((state) => state.loginWithMock)
  const markProfileMissing = useAuthStore((state) => state.markProfileMissing)
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
      window.localStorage.setItem(TOKEN_STORAGE_KEY, result.accessToken)

      if (env.useMocks) {
        loginWithMock({
          email: values.identifier.includes('@') ? values.identifier : `${values.identifier}@example.com`,
          role: (values.role ?? 'BUYER') as UserRole,
        })
        navigate('/dashboard')
        return
      }

      setSession(result.accessToken)

      try {
        const profile = await userApi.getMe()
        setUser(profile)
        navigate('/dashboard')
      } catch (error) {
        if (!isNotFoundError(error)) {
          throw error
        }

        markProfileMissing()
        navigate('/register-profile')
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
            <label>
              <span>Email hoặc tên đăng nhập</span>
              <input
                {...register('identifier')}
                type="text"
                autoComplete="username"
                placeholder="seller1 hoặc seller1@test.com"
              />
              {errors.identifier ? <small>{errors.identifier.message}</small> : null}
            </label>

            <label>
              <span>Mật khẩu</span>
              <input {...register('password')} type="password" />
              {errors.password ? <small>{errors.password.message}</small> : null}
            </label>

            {env.useMocks ? (
              <label>
                <span>Vai trò giả lập</span>
                <select {...register('role')}>
                  <option value="BUYER">Bên mua</option>
                  <option value="SELLER">Bên bán</option>
                  <option value="ADMIN">Quản trị</option>
                </select>
              </label>
            ) : null}

            {loginMutation.isError ? <small>Email hoặc mật khẩu không đúng.</small> : null}

            <button
              className="primary-button primary-button--full"
              type="submit"
              disabled={loginMutation.isPending}
            >
              {loginMutation.isPending ? 'Đang đăng nhập...' : 'Đăng nhập'}
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
