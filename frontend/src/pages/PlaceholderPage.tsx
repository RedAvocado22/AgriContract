interface PlaceholderPageProps {
  title: string
  body: string
}

export function PlaceholderPage({ title, body }: PlaceholderPageProps) {
  return (
    <div className="empty-state empty-state--panel">
      <h2>{title}</h2>
      <p>{body}</p>
    </div>
  )
}
