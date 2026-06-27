import type { Listing } from '../types/listing'

export const MOCK_LISTINGS: Listing[] = [
  {
    listingId: 'lst-robusta-daklak',
    productId: 'prd-001',
    sellerId: 'seller-001',
    sellerName: 'Hợp tác xã Cà phê Đắk Lắk',
    productName: 'Cà phê Robusta Đắk Lắk',
    category: 'Cà phê',
    description:
      'Cà phê nhân xô loại 1, độ ẩm đạt chuẩn xuất khẩu và sẵn sàng giao theo lô lớn.',
    quantity: 50,
    quantityUnit: 'Tấn',
    priceFloor: 65000,
    currency: 'VND',
    deliveryDeadline: '2026-11-15',
    status: 'ACTIVE',
    location: 'Đắk Lắk',
    qualityNotes: 'Độ ẩm dưới 12.5%, sàng lọc theo tiêu chuẩn xuất khẩu.',
    imageUrl:
      'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=1200&q=80',
  },
  {
    listingId: 'lst-st25-angiang',
    productId: 'prd-002',
    sellerId: 'seller-002',
    sellerName: 'Liên hiệp Lúa gạo An Giang',
    productName: 'Lúa gạo ST25 An Giang',
    category: 'Lúa gạo',
    description:
      'Gạo thơm ST25 đóng bao 50kg, phù hợp nhà mua sỉ và chuỗi phân phối xuất khẩu.',
    quantity: 100,
    quantityUnit: 'Tấn',
    priceFloor: 25000,
    currency: 'VND',
    deliveryDeadline: '2026-12-01',
    status: 'ACTIVE',
    location: 'An Giang',
    qualityNotes: 'Hạt đồng đều, tỷ lệ tấm thấp, có chứng nhận VietGAP.',
    imageUrl:
      'https://images.unsplash.com/photo-1586201375761-83865001e31c?auto=format&fit=crop&w=1200&q=80',
  },
  {
    listingId: 'lst-cashew-binhphuoc',
    productId: 'prd-003',
    sellerId: 'seller-003',
    sellerName: 'Công ty Điều Bình Phước',
    productName: 'Hạt điều thô Bình Phước',
    category: 'Điều',
    description:
      'Hạt điều thô thu mua trực tiếp từ vườn, tỷ lệ thu hồi nhân cao và truy xuất rõ ràng.',
    quantity: 20,
    quantityUnit: 'Tấn',
    priceFloor: 120000,
    currency: 'VND',
    deliveryDeadline: '2026-11-10',
    status: 'ACTIVE',
    location: 'Bình Phước',
    qualityNotes: 'Tỷ lệ thu hồi nhân 30%, đã qua phân loại và đóng bao.',
    imageUrl:
      'https://images.unsplash.com/photo-1502741338009-cac2772e18bc?auto=format&fit=crop&w=1200&q=80',
  },
  {
    listingId: 'lst-rubber-tayninh',
    productId: 'prd-004',
    sellerId: 'seller-004',
    sellerName: 'Tập đoàn Cao su Tây Ninh',
    productName: 'Mủ cao su thiên nhiên Tây Ninh',
    category: 'Cao su',
    description:
      'Mủ nước chất lượng cao, đã đóng giao dịch mẫu trước và hiện đang tạm đóng.',
    quantity: 30,
    quantityUnit: 'Tấn',
    priceFloor: 45000,
    currency: 'VND',
    deliveryDeadline: '2026-10-05',
    status: 'CLOSED',
    location: 'Tây Ninh',
    qualityNotes: 'DRC đạt 32%, phù hợp nhà máy sơ chế biến.',
    imageUrl:
      'https://images.unsplash.com/photo-1471193945509-9ad0617afabf?auto=format&fit=crop&w=1200&q=80',
  },
]
