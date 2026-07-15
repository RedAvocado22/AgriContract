const STORAGE_KEY = 'agricontract:listing-images'
const LEGACY_COVER_STORAGE_KEY = 'agricontract:listing-cover-images'
const MAX_IMAGE_SIDE = 960
const JPEG_QUALITY = 0.76

type StoredListingImages = Record<string, string[]>

const canUseStorage = () => typeof window !== 'undefined' && Boolean(window.localStorage)

const readStoredListingImages = (): StoredListingImages => {
  if (!canUseStorage()) {
    return {}
  }

  try {
    return JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? '{}') as StoredListingImages
  } catch {
    return {}
  }
}

const readFileAsDataUrl = (file: File) =>
  new Promise<string>((resolve, reject) => {
    const reader = new FileReader()

    reader.addEventListener('load', () => resolve(String(reader.result ?? '')))
    reader.addEventListener('error', () => reject(reader.error))
    reader.readAsDataURL(file)
  })

const loadImage = (source: string) =>
  new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image()

    image.addEventListener('load', () => resolve(image))
    image.addEventListener('error', () => reject(new Error('Cannot load selected image')))
    image.src = source
  })

const fileToCompressedDataUrl = async (file: File) => {
  const source = await readFileAsDataUrl(file)
  const image = await loadImage(source)
  const scale = Math.min(1, MAX_IMAGE_SIDE / Math.max(image.naturalWidth, image.naturalHeight))
  const width = Math.max(1, Math.round(image.naturalWidth * scale))
  const height = Math.max(1, Math.round(image.naturalHeight * scale))
  const canvas = document.createElement('canvas')
  const context = canvas.getContext('2d')

  if (!context) {
    return source
  }

  canvas.width = width
  canvas.height = height
  context.fillStyle = '#ffffff'
  context.fillRect(0, 0, width, height)
  context.drawImage(image, 0, 0, width, height)

  return canvas.toDataURL('image/jpeg', JPEG_QUALITY)
}

export const getLocalListingImages = (listingId: string) => {
  const storedListingImages = readStoredListingImages()
  const listingImages = storedListingImages[listingId]

  if (listingImages?.length) {
    return listingImages
  }

  if (!canUseStorage()) {
    return []
  }

  try {
    const legacyCoverImages = JSON.parse(
      window.localStorage.getItem(LEGACY_COVER_STORAGE_KEY) ?? '{}',
    ) as Record<string, string>

    return legacyCoverImages[listingId] ? [legacyCoverImages[listingId]] : []
  } catch {
    return []
  }
}

export const saveLocalListingImages = async (listingId: string, files: File[]) => {
  if (files.length === 0 || !canUseStorage()) {
    return
  }

  try {
    const imageDataUrls = await Promise.all(files.map(fileToCompressedDataUrl))
    const storedListingImages = readStoredListingImages()

    window.localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        ...storedListingImages,
        [listingId]: imageDataUrls,
      }),
    )
  } catch {
    try {
      const firstImageDataUrl = await fileToCompressedDataUrl(files[0])
      const storedListingImages = readStoredListingImages()

      window.localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
          ...storedListingImages,
          [listingId]: [firstImageDataUrl],
        }),
      )
    } catch {
      // The backend does not support file uploads yet; local image persistence is best-effort.
    }
  }
}
