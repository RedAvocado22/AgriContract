import { useEffect, useState, type CSSProperties, type KeyboardEvent } from 'react'

interface PriceRange {
  min: number
  max: number
}

interface DualRangeSliderProps {
  min: number
  max: number
  limitMin: number
  limitMax: number
  step: number
  onChange: (range: PriceRange) => void
}

type RangeStyle = CSSProperties & {
  '--range-start': string
  '--range-end': string
}

const formatPrice = (value: number) => value.toLocaleString('vi-VN')
const parsePrice = (value: string) => Number(value.replace(/\D/g, ''))

export function DualRangeSlider({
  min,
  max,
  limitMin,
  limitMax,
  step,
  onChange,
}: DualRangeSliderProps) {
  const [draftMin, setDraftMin] = useState(formatPrice(min))
  const [draftMax, setDraftMax] = useState(formatPrice(max))

  useEffect(() => setDraftMin(formatPrice(min)), [min])
  useEffect(() => setDraftMax(formatPrice(max)), [max])

  const commitMin = () => {
    const parsed = parsePrice(draftMin)
    const nextMin = Number.isFinite(parsed)
      ? Math.min(Math.max(parsed, limitMin), max)
      : min

    setDraftMin(formatPrice(nextMin))
    if (nextMin !== min) onChange({ min: nextMin, max })
  }

  const commitMax = () => {
    const parsed = parsePrice(draftMax)
    const nextMax = Number.isFinite(parsed)
      ? Math.max(Math.min(parsed, limitMax), min)
      : max

    setDraftMax(formatPrice(nextMax))
    if (nextMax !== max) onChange({ min, max: nextMax })
  }

  const commitOnEnter = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') event.currentTarget.blur()
  }

  const span = Math.max(limitMax - limitMin, step)
  const rangeStyle: RangeStyle = {
    '--range-start': `${((min - limitMin) / span) * 100}%`,
    '--range-end': `${((max - limitMin) / span) * 100}%`,
  }

  return (
    <div className="dual-range">
      <div className="price-input-grid">
        <label className="price-input">
          <span>Từ</span>
          <span className="price-input__control">
            <input
              aria-label="Giá tối thiểu"
              inputMode="numeric"
              value={draftMin}
              onChange={(event) => setDraftMin(event.target.value)}
              onBlur={commitMin}
              onFocus={(event) => event.currentTarget.select()}
              onKeyDown={commitOnEnter}
            />
            <span>₫</span>
          </span>
        </label>

        <label className="price-input">
          <span>Đến</span>
          <span className="price-input__control">
            <input
              aria-label="Giá tối đa"
              inputMode="numeric"
              value={draftMax}
              onChange={(event) => setDraftMax(event.target.value)}
              onBlur={commitMax}
              onFocus={(event) => event.currentTarget.select()}
              onKeyDown={commitOnEnter}
            />
            <span>₫</span>
          </span>
        </label>
      </div>

      <div className="dual-range__control" style={rangeStyle}>
        <div className="dual-range__track" />
        <input
          className="dual-range__input dual-range__input--min"
          type="range"
          aria-label="Giá tối thiểu"
          min={limitMin}
          max={limitMax}
          step={step}
          value={min}
          onChange={(event) =>
            onChange({ min: Math.min(Number(event.target.value), max), max })
          }
        />
        <input
          className="dual-range__input dual-range__input--max"
          type="range"
          aria-label="Giá tối đa"
          min={limitMin}
          max={limitMax}
          step={step}
          value={max}
          onChange={(event) =>
            onChange({ min, max: Math.max(Number(event.target.value), min) })
          }
        />
      </div>

      <div className="range-row" aria-live="polite">
        <span>{formatPrice(min)} ₫</span>
        <span>{formatPrice(max)} ₫ / kg</span>
      </div>
    </div>
  )
}
