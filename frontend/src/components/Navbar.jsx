import { LogOut, Menu } from 'lucide-react'
import { useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

const routeNames = {
  '/dashboard': 'Dashboard',
  '/defects': 'Defect register',
  '/defects/new': 'New defect',
  '/knowledge': 'Project knowledge',
  '/chat': 'AI Chatbot',
}

export default function Navbar({ onMenuClick }) {
  const { pathname } = useLocation()
  const { user, logout } = useAuth()
  const detailRoute = pathname.match(/^\/defects\/[^/]+$/) && pathname !== '/defects/new'
  const title = routeNames[pathname] || (pathname.startsWith('/chat/') ? 'AI Chatbot' : detailRoute ? 'Defect investigation workspace' : 'ImpactIQ')
  const name = user?.displayName || user?.username || ''
  const initials = name.split(/\s+/).map((part) => part[0]).join('').slice(0, 2).toUpperCase() || 'IQ'

  return (
    <header className="topbar">
      <button className="mobile-menu icon-button" onClick={onMenuClick} aria-label="Open navigation">
        <Menu size={19} />
      </button>
      <div className="topbar-title-block">
        <span className="eyebrow">IMPACTIQ / OPERATIONS</span>
        <h1>{title}</h1>
      </div>
      <div className="topbar-tools">
        <span className="topbar-date">Defect intelligence</span>
        <span className="workspace-chip" title={user?.role === 'ADMIN' ? 'Administrator' : 'Member'}>
          <span className="profile-avatar">{initials}</span><span className="workspace-name">{name}</span>
          {user?.role === 'ADMIN' && <span className="role-pill">ADMIN</span>}
        </span>
        <button className="icon-button" onClick={logout} aria-label="Sign out" title="Sign out"><LogOut size={17} /></button>
      </div>
    </header>
  )
}
