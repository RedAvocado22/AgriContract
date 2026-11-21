import { Link } from "react-router-dom"
import { AuthAction } from "@/features/auth/components/auth-action"

export function AuthPage({
  defaultView = "login",
}: {
  defaultView?: "login" | "register" | "forgot_password"
}) {
  return (
    <main className="flex min-h-screen items-center justify-center bg-page-bg p-4">
      <section className="w-full max-w-lg rounded-auth-card bg-surface p-6 shadow-auth-card sm:p-10">
        <Link to="/" className="mb-8 block text-xl font-medium text-primary">
          AgriContract
        </Link>
        <AuthAction key={defaultView} mode={defaultView} />
      </section>
    </main>
  )
}
