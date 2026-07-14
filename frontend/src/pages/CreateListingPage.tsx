import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'

import { listingApi } from '../api/listingApi'
import { productApi } from '../api/productApi'
import type { CreateListingInput } from '../types/listing'
import type { CreateProductInput } from '../types/product'

const createListingSchema = z.object({
  productName: z.string().min(3, 'Vui lòng nhập tên sản phẩm'),
  category: z.string().min(2, 'Vui lòng nhập danh mục'),
  unit: z.string().min(1, 'Vui lòng nhập đơn vị'),
  quantity: z.coerce.number().positive('Số lượng phải lớn hơn 0'),
  priceFloor: z.coerce.number().positive('Giá sàn phải lớn hơn 0'),
  deliveryDeadline: z.string().min(1, 'Vui lòng chọn thời hạn giao hàng'),
})

type CreateListingFormInput = z.input<typeof createListingSchema>
type CreateListingFormValues = z.output<typeof createListingSchema>

export function CreateListingPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<CreateListingFormInput, undefined, CreateListingFormValues>({
    resolver: zodResolver(createListingSchema),
    defaultValues: {
      productName: 'Gạo ST25 xuất khẩu',
      category: 'Lúa gạo',
      unit: 'kg',
      quantity: 50,
      priceFloor: 25000,
      deliveryDeadline: '2026-10-30',
    },
  })

  const unit = watch('unit')

  const createProductMutation = useMutation({
    mutationFn: (input: CreateProductInput) => productApi.create(input),
  })

  const createListingMutation = useMutation({
    mutationFn: (input: CreateListingInput) => listingApi.create(input),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['listings'] })
      await queryClient.invalidateQueries({ queryKey: ['products'] })
      navigate('/listings')
    },
  })

  const onSubmit = async (values: CreateListingFormValues) => {
    const product = await createProductMutation.mutateAsync({
      name: values.productName,
      category: values.category,
      unit: values.unit,
    })

    await createListingMutation.mutateAsync({
      productId: product.productId,
      quantity: values.quantity,
      quantityUnit: values.unit,
      priceFloor: values.priceFloor,
      deliveryDeadline: values.deliveryDeadline,
    })
  }

  const isPending = createProductMutation.isPending || createListingMutation.isPending

  return (
    <div className="form-page">
      <section className="page-header">
        <h1>Tạo tin đăng mới</h1>
        <p>Nhập sản phẩm mới theo cách tự nhiên, rồi chúng ta sẽ đăng tin từ chính sản phẩm đó.</p>
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
            <input {...register('category')} list="product-categories" />
            <datalist id="product-categories">
              <option value="Lúa gạo" />
              <option value="Cà phê" />
              <option value="Điều" />
              <option value="Cao su" />
            </datalist>
            {errors.category ? <small>{errors.category.message}</small> : null}
          </label>

          <label>
            <span>Đơn vị</span>
            <input {...register('unit')} />
            {errors.unit ? <small>{errors.unit.message}</small> : null}
          </label>

          <label>
            <span>Số lượng</span>
            <input {...register('quantity')} type="number" />
            {errors.quantity ? <small>{errors.quantity.message}</small> : null}
          </label>

          <label>
            <span>Giá sàn (VND/{unit || 'đơn vị'})</span>
            <input {...register('priceFloor')} type="number" />
            {errors.priceFloor ? <small>{errors.priceFloor.message}</small> : null}
          </label>

          <label>
            <span>Thời hạn giao hàng</span>
            <input {...register('deliveryDeadline')} type="date" />
            {errors.deliveryDeadline ? <small>{errors.deliveryDeadline.message}</small> : null}
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
          <button className="primary-button" type="submit" disabled={isPending}>
            {isPending ? 'Đang đăng tin...' : 'Đăng tin'}
          </button>
        </div>
      </form>
    </div>
  )
}
