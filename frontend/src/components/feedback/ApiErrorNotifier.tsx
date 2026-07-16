import { useEffect, useState } from 'react'

export const API_ERROR_EVENT = 'agricontract:api-error'

export function ApiErrorNotifier() {
  const [message, setMessage] = useState('')

  useEffect(() => {
    let timeoutId: number | undefined
    const handleError = (event: Event) => {
      const customEvent = event as CustomEvent<string>
      window.clearTimeout(timeoutId)
      setMessage(customEvent.detail)
      timeoutId = window.setTimeout(() => setMessage(''), 5000)
    }

    window.addEventListener(API_ERROR_EVENT, handleError)
    return () => {
      window.clearTimeout(timeoutId)
      window.removeEventListener(API_ERROR_EVENT, handleError)
    }
  }, [])

  return message ? (
    <div className="api-error-toast" role="alert">
      {message}
      <button type="button" aria-label="Đóng thông báo lỗi" onClick={() => setMessage('')}>
        ×
      </button>
    </div>
  ) : null
}
