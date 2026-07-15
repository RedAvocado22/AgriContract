import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { ChangeEvent } from 'react'
import { useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'

import { listingApi } from '../api/listingApi'
import { productApi } from '../api/productApi'
import { env } from '../config/env'
import type { CreateListingInput } from '../types/listing'
import type { CreateProductInput } from '../types/product'
import { saveLocalListingImages } from '../utils/localListingImages'
import { PRODUCT_CATEGORIES } from '../utils/productCategory'

const MAX_IMAGE_FILES = 5
const LISTING_CURRENCY = 'VND'

const getTodayInputDate = () => {
  const today = new Date()
  const timezoneOffsetMs = today.getTimezoneOffset() * 60 * 1000

  return new Date(today.getTime() - timezoneOffsetMs).toISOString().slice(0, 10)
}

const getDefaultDeliveryDate = () => {
  const date = new Date()

  date.setDate(date.getDate() + 90)

  const timezoneOffsetMs = date.getTimezoneOffset() * 60 * 1000
  return new Date(date.getTime() - timezoneOffsetMs).toISOString().slice(0, 10)
}

const sanitizeIntegerInput = (input: HTMLInputElement) => {
  input.value = input.value.split(/[.,]/)[0].replace(/[^\d]/g, '')
}

const translateCreateListingMessage = (message: string | undefined) => {
  if (!message) {
    return undefined
  }

  if (
    message.includes('deliveryDeadline') &&
    message.includes('must be a date in the present or in the future')
  ) {
    return 'Hạn giao hàng phải là hôm nay hoặc một ngày trong tương lai.'
  }

  if (message.includes('must be a date in the present or in the future')) {
    return 'Ngày giao hàng phải là hôm nay hoặc một ngày trong tương lai.'
  }

  if (message.includes('quantity') && message.includes('integer')) {
    return 'Số lượng phải là số nguyên.'
  }

  return message
}

interface SelectedImage {
  id: string
  file: File
  previewUrl: string
}

const getCreateListingError = (error: unknown) => {
  const apiError = error as {
    response?: {
      status?: number
      data?: {
        message?: string
        error?: string
      }
    }
    message?: string
  }
  const status = apiError.response?.status
  const message = translateCreateListingMessage(
    apiError.response?.data?.message ?? apiError.response?.data?.error ?? apiError.message,
  )

  if (status === 401) {
    return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại rồi đăng tin.'
  }

  if (status === 403) {
    return `Máy chủ từ chối yêu cầu đăng tin. Hãy kiểm tra frontend đang gọi qua API Gateway (${env.apiBaseUrl}).`
  }

  if (message?.includes('Category')) {
    return `${message}. Khởi động lại product-service để tạo các loại hàng mặc định.`
  }

  if (status) {
    return `Máy chủ trả về ${status}: ${message ?? 'không đăng được tin hàng.'}`
  }

  return message ?? 'Không đăng được tin hàng. Vui lòng kiểm tra máy chủ và thử lại.'
}

const createListingSchema = z.object({
  productName: z
    .string()
    .min(1, 'Nhập tên sản phẩm')
    .min(3, 'Tên sản phẩm cần ít nhất 3 ký tự'),
  category: z.string().min(2, 'Chọn loại hàng'),
  unit: z.string().min(1, 'Nhập đơn vị'),
  quantity: z.coerce.number().int('Số lượng phải là số nguyên').positive('Số lượng phải lớn hơn 0'),
  priceFloor: z.coerce.number().positive('Giá sàn phải lớn hơn 0'),
  deliveryDeadline: z
    .string()
    .min(1, 'Chọn hạn giao hàng')
    .refine((value) => value >= getTodayInputDate(), 'Hạn giao hàng phải là hôm nay hoặc một ngày trong tương lai'),
})

type CreateListingFormInput = z.input<typeof createListingSchema>
type CreateListingFormValues = z.output<typeof createListingSchema>

export function CreateListingPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const [selectedImages, setSelectedImages] = useState<SelectedImage[]>([])
  const selectedImagesRef = useRef<SelectedImage[]>([])
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<CreateListingFormInput, undefined, CreateListingFormValues>({
    resolver: zodResolver(createListingSchema),
    defaultValues: {
      productName: '',
      category: '',
      unit: 'ton',
      quantity: undefined,
      priceFloor: undefined,
      deliveryDeadline: getDefaultDeliveryDate(),
    },
  })

  useEffect(() => {
    selectedImagesRef.current = selectedImages
  }, [selectedImages])

  useEffect(
    () => () => {
      selectedImagesRef.current.forEach((image) => URL.revokeObjectURL(image.previewUrl))
    },
    [],
  )

  const unit = watch('unit')
  const todayInputDate = getTodayInputDate()
  const remainingImageSlots = MAX_IMAGE_FILES - selectedImages.length
  const imageLimitReached = remainingImageSlots === 0

  const createProductMutation = useMutation({
    mutationFn: (input: CreateProductInput) => productApi.create(input),
  })

  const createListingMutation = useMutation({
    mutationFn: (input: CreateListingInput) => listingApi.create(input),
    onSuccess: async (listing) => {
      await saveLocalListingImages(
        listing.listingId,
        selectedImages.map((image) => image.file),
      )
      await queryClient.invalidateQueries({ queryKey: ['listings'] })
      await queryClient.invalidateQueries({ queryKey: ['products'] })
      navigate(`/listings/${listing.listingId}`)
    },
  })

  const handleImageChange = (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? [])
      .filter((file) => file.type.startsWith('image/'))
      .slice(0, remainingImageSlots)
      .map((file) => ({
        id: `${file.name}-${file.size}-${crypto.randomUUID()}`,
        file,
        previewUrl: URL.createObjectURL(file),
      }))

    if (files.length > 0) {
      setSelectedImages((current) => [...current, ...files].slice(0, MAX_IMAGE_FILES))
    }

    event.target.value = ''
  }

  const removeImage = (imageId: string) => {
    setSelectedImages((current) => {
      const image = current.find((item) => item.id === imageId)
      if (image) {
        URL.revokeObjectURL(image.previewUrl)
      }

      return current.filter((item) => item.id !== imageId)
    })
  }

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
      priceFloor: Math.round(values.priceFloor),
      currency: LISTING_CURRENCY,
      deliveryDeadline: values.deliveryDeadline,
    })
  }

  const isPending = createProductMutation.isPending || createListingMutation.isPending
  const createError = createProductMutation.error ?? createListingMutation.error

  return (
    <div className="form-page">
      <section className="page-header">
        <h1>Đăng tin hàng</h1>
        <p>Nhập thông tin lô hàng để người mua có thể tạo đề nghị hợp đồng.</p>
      </section>

      <form className="listing-form" noValidate onSubmit={handleSubmit(onSubmit)}>
        <div className="form-grid">
          <label>
            <span>Tên sản phẩm</span>
            <input {...register('productName')} />
            {errors.productName ? <small>{errors.productName.message}</small> : null}
          </label>

          <label>
            <span>Loại hàng</span>
            <select {...register('category')}>
              <option value="">Chọn loại hàng</option>
              {PRODUCT_CATEGORIES.map((categoryOption) => (
                <option key={categoryOption.value} value={categoryOption.value}>
                  {categoryOption.label}
                </option>
              ))}
            </select>
            {errors.category ? <small>{errors.category.message}</small> : null}
          </label>

          <label>
            <span>Đơn vị</span>
            <input {...register('unit')} placeholder="tấn, kg, bao" />
            {errors.unit ? <small>{errors.unit.message}</small> : null}
          </label>

          <label>
            <span>Số lượng</span>
            <input
              {...register('quantity')}
              type="number"
              inputMode="numeric"
              min="1"
              step="1"
              onInput={(event) => sanitizeIntegerInput(event.currentTarget)}
            />
            {errors.quantity ? <small>{errors.quantity.message}</small> : null}
          </label>

          <label>
            <span>Giá sàn ({LISTING_CURRENCY}/{unit || 'đơn vị'})</span>
            <input
              {...register('priceFloor')}
              type="number"
              min="1"
              step="1"
            />
            {errors.priceFloor ? <small>{errors.priceFloor.message}</small> : null}
          </label>

          <label>
            <span>Tiền tệ</span>
            <div className="readonly-field">VND</div>
          </label>

          <label>
            <span>Hạn giao hàng</span>
            <input {...register('deliveryDeadline')} type="date" min={todayInputDate} />
            {errors.deliveryDeadline ? <small>{errors.deliveryDeadline.message}</small> : null}
          </label>

          <div className="form-grid__full image-upload-field">
            <div>
              <span className="field-label">Ảnh sản phẩm</span>
              <input
                ref={fileInputRef}
                accept="image/*"
                className="visually-hidden"
                multiple
                type="file"
                onChange={handleImageChange}
              />
              <div
                className="image-upload-action"
                data-tooltip={imageLimitReached ? 'Đã đủ 5 ảnh. Xóa bớt ảnh để thêm ảnh mới.' : undefined}
                tabIndex={imageLimitReached ? 0 : undefined}
              >
                <button
                  className="image-upload-button"
                  type="button"
                  disabled={imageLimitReached}
                  onClick={() => fileInputRef.current?.click()}
                >
                  <span className="material-symbols-outlined">add_photo_alternate</span>
                  {selectedImages.length > 0 ? 'Thêm ảnh' : 'Chọn ảnh từ máy/điện thoại'}
                </button>
              </div>
              <small>Tối đa 5 ảnh.</small>
            </div>

            {selectedImages.length > 0 ? (
              <div className="image-upload-preview" aria-label="Ảnh sản phẩm đã chọn">
                {selectedImages.map((image, index) => (
                  <div className="image-upload-preview__item" key={image.id}>
                    <img src={image.previewUrl} alt={`Ảnh sản phẩm ${index + 1}`} />
                    <button
                      aria-label={`Xóa ảnh ${index + 1}`}
                      type="button"
                      onClick={() => removeImage(image.id)}
                    >
                      <span className="material-symbols-outlined">close</span>
                    </button>
                  </div>
                ))}
              </div>
            ) : null}
          </div>
        </div>

        <div className="form-actions">
          {createError ? (
            <div className="notice-panel notice-panel--danger form-actions__notice" role="alert">
              <span className="material-symbols-outlined">error</span>
              <p>{getCreateListingError(createError)}</p>
            </div>
          ) : null}

          <button className="ghost-button" type="button" onClick={() => navigate('/listings')}>
            Hủy
          </button>
          <button className="primary-button" type="submit" disabled={isPending}>
            {isPending ? 'Đang đăng tin...' : 'Đăng tin hàng'}
          </button>
        </div>
      </form>
    </div>
  )
}
