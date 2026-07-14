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
  identifier: z.string().trim().min(1, 'Enter your email or username'),
  password: z.string().nonempty('Enter your password'),
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
      <AuthHeader
        action={
          <Link className="ghost-button" to="/listings">
            Browse marketplace
          </Link>
        }
      />
      <section className="auth-page auth-page--split">
        <aside className="auth-rail">
          <div className="auth-rail__brand">
            <div className="brand__mark">eco</div>
            <div>
              <strong>AgriContract</strong>
              <span>Trade, escrow, settle</span>
            </div>
          </div>
          <nav className="auth-rail__nav" aria-label="Public navigation">
            <Link className="sidebar-link" to="/listings">
              <span className="material-symbols-outlined">list_alt</span>
              Listings
            </Link>
          </nav>
        </aside>

        <div className="auth-card">
          <div className="auth-card__hero">
            <div className="brand-brandmark">eco</div>
            <h1>AgriContract</h1>
            <p>Contract-ready agricultural trade with escrow-backed settlement.</p>
          </div>

          <form className="auth-form" onSubmit={handleSubmit(onSubmit)}>
            <label>
              <span>Email or username</span>
              <input
                {...register('identifier')}
                type="text"
                autoComplete="username"
                placeholder="buyer1 or buyer1@example.com"
              />
              {errors.identifier ? <small>{errors.identifier.message}</small> : null}
            </label>

            <label>
              <span>Password</span>
              <input {...register('password')} type="password" />
              {errors.password ? <small>{errors.password.message}</small> : null}
            </label>

            {env.useMocks ? (
              <label>
                <span>Mock role</span>
                <select {...register('role')}>
                  <option value="BUYER">Buyer</option>
                  <option value="SELLER">Seller</option>
                  <option value="ADMIN">Admin</option>
                </select>
              </label>
            ) : null}

            {loginMutation.isError ? <small>Email or password is incorrect.</small> : null}

            <button
              className="primary-button primary-button--full"
              type="submit"
              disabled={loginMutation.isPending}
            >
              {loginMutation.isPending ? 'Signing in...' : 'Sign in'}
              <span className="material-symbols-outlined">arrow_forward</span>
            </button>
          </form>

          <div className="auth-trust">
            <div>
              <span className="material-symbols-outlined">verified_user</span>
              <span>Verified profiles</span>
            </div>
            <div>
              <span className="material-symbols-outlined">lock</span>
              <span>Escrow protection</span>
            </div>
            <div>
              <span className="material-symbols-outlined">gavel</span>
              <span>Dispute workflow</span>
            </div>
          </div>
        </div>
      </section>
    </>
  )
}
