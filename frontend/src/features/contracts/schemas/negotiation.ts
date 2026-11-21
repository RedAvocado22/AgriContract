import { z } from "zod"
export const negotiationSchema = z.object({
  agreedPrice: z
    .string()
    .regex(
      /^\d{1,8}\.\d{2}$/,
      "Nhập đơn giá VND/kg, tối đa 8 chữ số nguyên và đúng 2 chữ số thập phân (ví dụ 95000.00)."
    ),
})
