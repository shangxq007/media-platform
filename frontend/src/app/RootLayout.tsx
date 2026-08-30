import { Outlet } from '@tanstack/react-router'

export default function RootLayout() {
  return (
    <div className="ff-root" data-theme="dark">
      <Outlet />
    </div>
  )
}
