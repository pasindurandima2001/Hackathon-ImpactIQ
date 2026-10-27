import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import LoadingState from './LoadingState.jsx'

/** Signed-in users only; with adminOnly, other users are sent to the dashboard. */
export default function RequireAuth({ children, adminOnly = false }) {
  const { user, checking } = useAuth()
  const location = useLocation()
  if (checking) return <div className="auth-shell"><LoadingState label="Checking your session" /></div>
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (adminOnly && user.role !== 'ADMIN') return <Navigate to="/dashboard" replace />
  return children
}
