import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { z } from 'zod'

import { authApi, TOKEN_STORAGE_KEY } from '../api/authApi'
import { isNotFoundError, userApi } from '../api/userApi'
import { useAuthStore } from '../stores/authStore'

const loginSchema = z.object({
  email: z.email('Vui lòng nhập email hợp lệ'),
  password: z.string().nonempty('Vui lòng điền thông tin đăng nhập'),
})

type LoginFormValues = z.infer<typeof loginSchema>

export function LoginPage() {
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)
  const setSession = useAuthStore((state) => state.setSession)
  const setUser = useAuthStore((state) => state.setUser)
  const markProfileMissing = useAuthStore((state) => state.markProfileMissing)
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: '',
      password: '',
    },
  })

  const loginMutation = useMutation({
    mutationFn: authApi.login,
    onSuccess: async (result) => {
      window.localStorage.setItem(TOKEN_STORAGE_KEY, result.accessToken)
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
    <section className="auth-page">
      <div className="auth-card">
        <div className="auth-card__hero">
          <div className="brand-brandmark">eco</div>
          <h1>AgriContract</h1>
          <p>Nền tảng số cho nông nghiệp Việt Nam</p>
        </div>

        <form className="auth-form" onSubmit={handleSubmit(onSubmit)}>
          <label>
            <span>Email / Tên đăng nhập</span>
            <input {...register('email')} type="email"/>
            {errors.email ? <small>{errors.email.message}</small> : null}
          </label>

          <label>
            <span>Mật khẩu</span>
            <input
              {...register('password')}
              type="password"
            />
            {errors.password ? <small>{errors.password.message}</small> : null}
          </label>

          {loginMutation.isError ? (
            <small>Email hoặc mật khẩu không đúng.</small>
          ) : null}

          <button
            className="primary-button primary-button--full"
            type="submit"
            disabled={loginMutation.isPending}
          >
            {loginMutation.isPending ? 'Đang đăng nhập...' : 'Đăng nhập'}
            <span className="material-symbols-outlined">arrow_forward</span>
          </button>
        </form>

        <p className="auth-footer">
          Chưa có tài khoản? <Link to="/signup">Đăng ký ngay</Link>
        </p>

        <div className="auth-trust">
          <div>
            <span className="material-symbols-outlined">verified_user</span>
            <span>Hợp đồng thông minh</span>
          </div>
          <div>
            <span className="material-symbols-outlined">lock</span>
            <span>Escrow an toàn</span>
          </div>
          <div>
            <span className="material-symbols-outlined">gavel</span>
            <span>Trọng tài minh bạch</span>
          </div>
        </div>
      </div>
    </section>
  )
}
