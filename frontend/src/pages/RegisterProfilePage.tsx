import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Navigate, useNavigate } from 'react-router-dom'
import { z } from 'zod'

import { userApi } from '../api/userApi'
import { AuthHeader } from '../components/layout/AuthHeader'
import { useAuthStore } from '../stores/authStore'

const registerProfileSchema = z.object({
  organizationName: z.string().min(2, 'Enter your organization name'),
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
    <>
      <AuthHeader />
      <section className="auth-page">
        <div className="auth-card auth-card--wide">
          <div className="auth-card__hero">
            <div className="brand-brandmark">eco</div>
            <h1>Complete profile</h1>
            <p>This information is stored in the user service and shown on trading screens.</p>
          </div>

          <form className="auth-form" onSubmit={handleSubmit(onSubmit)}>
            <label>
              <span>Organization name</span>
              <input {...register('organizationName')} placeholder="Example: Dak Lak Coffee Cooperative" />
              {errors.organizationName ? <small>{errors.organizationName.message}</small> : null}
            </label>

            <label>
              <span>Phone</span>
              <input {...register('phone')} placeholder="+84 901 234 567" />
            </label>

            <label>
              <span>Address</span>
              <input {...register('address')} placeholder="Dak Lak, Vietnam" />
            </label>

            {registerMutation.isError ? <small>Profile could not be saved. Please try again.</small> : null}

            <button className="primary-button" type="submit" disabled={registerMutation.isPending}>
              {registerMutation.isPending ? 'Saving...' : 'Save profile'}
            </button>
          </form>
        </div>
      </section>
    </>
  )
}
