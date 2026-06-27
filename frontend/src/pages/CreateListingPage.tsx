import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'

import { listingApi } from '../api/listingApi'

const createListingSchema = z.object({
  productName: z.string().min(3, 'Vui lòng nhập tên sản phẩm'),
  category: z.string().min(2, 'Vui lòng chọn danh mục'),
  quantity: z.coerce.number().positive('Số lượng phải lớn hơn 0'),
  quantityUnit: z.string().min(1, 'Vui lòng chọn đơn vị'),
  priceFloor: z.coerce.number().positive('Giá sàn phải lớn hơn 0'),
  deliveryDeadline: z.string().min(1, 'Vui lòng chọn thời hạn giao hàng'),
  description: z.string().min(20, 'Vui lòng mô tả chi tiết hơn'),
  qualityNotes: z.string().min(12, 'Vui lòng nhập tiêu chuẩn chất lượng'),
  location: z.string().min(2, 'Vui lòng nhập khu vực'),
})

type CreateListingFormInput = z.input<typeof createListingSchema>
type CreateListingFormValues = z.output<typeof createListingSchema>

export function CreateListingPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<CreateListingFormInput, undefined, CreateListingFormValues>({
    resolver: zodResolver(createListingSchema),
    defaultValues: {
      productName: 'Gạo ST25 xuất khẩu',
      category: 'Lúa gạo',
      quantity: 50,
      quantityUnit: 'Tấn',
      priceFloor: 25000,
      deliveryDeadline: '2026-10-30',
      description:
        'Lô gạo đồng đều cho nhà mua sỉ, thích hợp đơn hàng xuất khẩu và phân phối nội địa.',
      qualityNotes: 'Hạt đồng đều, tỷ lệ tấm thấp, có chứng nhận VietGAP.',
      location: 'An Giang',
    },
  })

  const createListingMutation = useMutation({
    mutationFn: listingApi.create,
    onSuccess: async (listing) => {
      await queryClient.invalidateQueries({ queryKey: ['listings'] })
      navigate(`/listings/${listing.listingId}`)
    },
  })

  const onSubmit = (values: CreateListingFormValues) => {
    createListingMutation.mutate(values)
  }

  return (
    <div className="form-page">
      <section className="page-header">
        <h1>Tạo Tin Đăng Mới</h1>
        <p>Vui lòng điền thông tin chi tiết về sản phẩm nông nghiệp bạn muốn chào bán.</p>
      </section>

      <form className="listing-form" onSubmit={handleSubmit(onSubmit)}>
        <div className="form-grid">
          <label>
            <span>Tên sản phẩm</span>
            <input {...register('productName')} />
            {errors.productName ? <small>{errors.productName.message}</small> : null}
          </label>

          <label>
            <span>Danh mục</span>
            <select {...register('category')}>
              <option value="Lúa gạo">Lúa gạo</option>
              <option value="Cà phê">Cà phê</option>
              <option value="Điều">Điều</option>
              <option value="Cao su">Cao su</option>
            </select>
            {errors.category ? <small>{errors.category.message}</small> : null}
          </label>

          <label>
            <span>Số lượng</span>
            <input {...register('quantity')} type="number" />
            {errors.quantity ? <small>{errors.quantity.message}</small> : null}
          </label>

          <label>
            <span>Đơn vị</span>
            <select {...register('quantityUnit')}>
              <option value="Tấn">Tấn</option>
              <option value="Kg">Kg</option>
              <option value="Container 20ft">Container 20ft</option>
            </select>
          </label>

          <label>
            <span>Giá sàn (VND/kg)</span>
            <input {...register('priceFloor')} type="number" />
            {errors.priceFloor ? <small>{errors.priceFloor.message}</small> : null}
          </label>

          <label>
            <span>Thời hạn giao hàng</span>
            <input {...register('deliveryDeadline')} type="date" />
            {errors.deliveryDeadline ? (
              <small>{errors.deliveryDeadline.message}</small>
            ) : null}
          </label>

          <label>
            <span>Khu vực</span>
            <input {...register('location')} />
            {errors.location ? <small>{errors.location.message}</small> : null}
          </label>

          <label className="form-grid__full">
            <span>Mô tả chi tiết</span>
            <textarea {...register('description')} rows={4} />
            {errors.description ? <small>{errors.description.message}</small> : null}
          </label>

          <label className="form-grid__full">
            <span>Tiêu chuẩn chất lượng</span>
            <textarea {...register('qualityNotes')} rows={3} />
            {errors.qualityNotes ? <small>{errors.qualityNotes.message}</small> : null}
          </label>
        </div>

        <div className="form-actions">
          <button
            className="ghost-button"
            type="button"
            onClick={() => navigate('/listings')}
          >
            Hủy bỏ
          </button>
          <button
            className="primary-button"
            type="submit"
            disabled={createListingMutation.isPending}
          >
            {createListingMutation.isPending ? 'Đang đăng tin...' : 'Đăng tin'}
          </button>
        </div>
      </form>
    </div>
  )
}
