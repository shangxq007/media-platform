import { Outlet } from '@tanstack/react-router'

export default function RootLayout() {
  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <Outlet />
    </div>
  )
}
