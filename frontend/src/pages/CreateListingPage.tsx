import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'

import { listingApi } from '../api/listingApi'
import { productApi } from '../api/productApi'
import type { CreateListingInput } from '../types/listing'
import type { CreateProductInput } from '../types/product'
import { PRODUCT_CATEGORIES } from '../utils/productCategory'

const createListingSchema = z.object({
  productName: z.string().min(3, 'Enter a product name'),
  category: z.string().min(2, 'Choose a category'),
  unit: z.string().min(1, 'Enter a unit'),
  quantity: z.coerce.number().positive('Quantity must be greater than 0'),
  priceFloor: z.coerce.number().positive('Floor price must be greater than 0'),
  currency: z.string().min(3, 'Currency is required'),
  deliveryDeadline: z.string().min(1, 'Choose a delivery deadline'),
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
      productName: 'Dak Lak Robusta coffee',
      category: 'COFFEE',
      unit: 'ton',
      quantity: 50,
      priceFloor: 65000,
      currency: 'VND',
      deliveryDeadline: '2026-10-30',
    },
  })

  const unit = watch('unit')
  const currency = watch('currency')

  const createProductMutation = useMutation({
    mutationFn: (input: CreateProductInput) => productApi.create(input),
  })

  const createListingMutation = useMutation({
    mutationFn: (input: CreateListingInput) => listingApi.create(input),
    onSuccess: async (listing) => {
      await queryClient.invalidateQueries({ queryKey: ['listings'] })
      await queryClient.invalidateQueries({ queryKey: ['products'] })
      navigate(`/listings/${listing.listingId}`)
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
      currency: values.currency,
      deliveryDeadline: values.deliveryDeadline,
    })
  }

  const isPending = createProductMutation.isPending || createListingMutation.isPending

  return (
    <div className="form-page">
      <section className="page-header">
        <h1>Create listing</h1>
        <p>Add the product first, then publish a contract-ready listing from the same form.</p>
      </section>

      <form className="listing-form" onSubmit={handleSubmit(onSubmit)}>
        <div className="form-grid">
          <label>
            <span>Product name</span>
            <input {...register('productName')} />
            {errors.productName ? <small>{errors.productName.message}</small> : null}
          </label>

          <label>
            <span>Category</span>
            <select {...register('category')}>
              {PRODUCT_CATEGORIES.map((category) => (
                <option key={category.value} value={category.value}>
                  {category.label}
                </option>
              ))}
            </select>
            {errors.category ? <small>{errors.category.message}</small> : null}
          </label>

          <label>
            <span>Unit</span>
            <input {...register('unit')} placeholder="ton, kg, bag" />
            {errors.unit ? <small>{errors.unit.message}</small> : null}
          </label>

          <label>
            <span>Quantity</span>
            <input {...register('quantity')} type="number" min="0.001" step="0.001" />
            {errors.quantity ? <small>{errors.quantity.message}</small> : null}
          </label>

          <label>
            <span>Floor price ({currency || 'currency'}/{unit || 'unit'})</span>
            <input {...register('priceFloor')} type="number" min="0.01" step="1000" />
            {errors.priceFloor ? <small>{errors.priceFloor.message}</small> : null}
          </label>

          <label>
            <span>Currency</span>
            <select {...register('currency')}>
              <option value="VND">VND</option>
              <option value="USD">USD</option>
            </select>
            {errors.currency ? <small>{errors.currency.message}</small> : null}
          </label>

          <label>
            <span>Delivery deadline</span>
            <input {...register('deliveryDeadline')} type="date" />
            {errors.deliveryDeadline ? <small>{errors.deliveryDeadline.message}</small> : null}
          </label>
        </div>

        <div className="form-actions">
          <button className="ghost-button" type="button" onClick={() => navigate('/listings')}>
            Cancel
          </button>
          <button className="primary-button" type="submit" disabled={isPending}>
            {isPending ? 'Publishing...' : 'Publish listing'}
          </button>
        </div>
      </form>
    </div>
  )
}
