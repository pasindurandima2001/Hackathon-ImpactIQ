import { Activity, AlertTriangle, BookOpenText, FilePlus2, LayoutDashboard, MessagesSquare, Plus, ShieldCheck } from 'lucide-react'
import { NavLink } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

const links = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/defects', label: 'Defects', icon: AlertTriangle, end: true },
  { to: '/defects/new', label: 'New defect', icon: FilePlus2 },
  { to: '/knowledge', label: 'Project knowledge', icon: BookOpenText, adminOnly: true },
  { to: '/chat', label: 'AI Chatbot', icon: MessagesSquare },
]

export default function Sidebar() {
  const { user } = useAuth()
  const visibleLinks = links.filter((link) => !link.adminOnly || user?.role === 'ADMIN')
  return (
    <aside className="sidebar">
      <NavLink className="brand-lockup" to="/dashboard" aria-label="ImpactIQ dashboard">
        <span className="brand-mark"><Activity size={20} strokeWidth={2.5} /></span>
        <span className="brand-name">impact<span>IQ</span></span>
      </NavLink>

      <div className="sidebar-section-label">WORKSPACE</div>
      <nav className="sidebar-nav" aria-label="Main navigation">
        {visibleLinks.map(({ to, label, icon: Icon, end }) => (
          <NavLink key={to} to={to} end={end} className={({ isActive }) => `sidebar-link${isActive ? ' active' : ''}`}>
            <Icon size={18} />
            <span>{label}</span>
          </NavLink>
        ))}
      </nav>

      <div className="sidebar-bottom">
        <div className="sidebar-status-icon"><ShieldCheck size={17} /></div>
        <div>
          <strong>Investigation agents</strong>
          <span><i className="online-dot" /> 6-agent workflow</span>
        </div>
        <NavLink to="/defects/new" className="sidebar-add" aria-label="Create defect" title="Create defect">
          <Plus size={17} />
        </NavLink>
      </div>
    </aside>
  )
}
