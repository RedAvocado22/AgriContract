import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Navigate, useNavigate } from 'react-router-dom'
import { z } from 'zod'

import { userApi } from '../api/userApi'
import { useAuthStore } from '../stores/authStore'

const registerProfileSchema = z.object({
  organizationName: z.string().min(2, 'Vui lòng nhập tên tổ chức'),
  phone: z.string().optional(),
  address: z.string().optional(),
})

type RegisterProfileValues = z.infer<typeof registerProfileSchema>

export function RegisterProfilePage() {
  const navigate = useNavigate()
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const profileStatus = useAuthStore((state) => state.profileStatus)
  const setUser = useAuthStore((state) => state.setUser)
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterProfileValues>({
    resolver: zodResolver(registerProfileSchema),
    defaultValues: {
      organizationName: '',
      phone: '',
      address: '',
    },
  })

  const registerMutation = useMutation({
    mutationFn: userApi.registerProfile,
    onSuccess: (profile) => {
      setUser(profile)
      navigate('/dashboard', { replace: true })
    },
  })

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  if (profileStatus === 'ready') {
    return <Navigate to="/dashboard" replace />
  }

  const onSubmit = (values: RegisterProfileValues) => {
    registerMutation.mutate(values)
  }

  return (
    <section className="auth-page">
      <div className="auth-card auth-card--wide">
        <div className="auth-card__hero">
          <div className="brand-brandmark">eco</div>
          <h1>Hoàn tất hồ sơ</h1>
          <p>Thông tin này sẽ được lưu vào cơ sở dữ liệu AgriContract.</p>
        </div>

        <form className="auth-form" onSubmit={handleSubmit(onSubmit)}>
          <label>
            <span>Tên tổ chức</span>
            <input
              {...register('organizationName')}
              placeholder="VD: Hợp tác xã Cà phê Đắk Lắk"
            />
            {errors.organizationName ? (
              <small>{errors.organizationName.message}</small>
            ) : null}
          </label>

          <label>
            <span>Số điện thoại</span>
            <input {...register('phone')} placeholder="0901234567" />
          </label>

          <label>
            <span>Địa chỉ</span>
            <input {...register('address')} placeholder="Đắk Lắk, Việt Nam" />
          </label>

          {registerMutation.isError ? (
            <small>Không thể lưu hồ sơ. Vui lòng thử lại.</small>
          ) : null}

          <button
            className="primary-button"
            type="submit"
            disabled={registerMutation.isPending}
          >
            {registerMutation.isPending ? 'Đang lưu...' : 'Lưu hồ sơ'}
          </button>
        </form>
      </div>
    </section>
  )
}
