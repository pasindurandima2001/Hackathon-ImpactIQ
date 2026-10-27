import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import Navbar from './Navbar.jsx'
import Sidebar from './Sidebar.jsx'

export default function Layout() {
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <div className="app-shell">
      <div className={`sidebar-wrap${menuOpen ? ' open' : ''}`}>
        <Sidebar />
      </div>
      {menuOpen && <button className="mobile-scrim" onClick={() => setMenuOpen(false)} aria-label="Close navigation" />}
      <div className="main-shell">
        <Navbar onMenuClick={() => setMenuOpen((open) => !open)} />
        <main className="page-content" onClick={() => menuOpen && setMenuOpen(false)}>
          <Outlet />
          <footer className="app-footer"><span>ImpactIQ</span><span>Defect impact operations</span></footer>
        </main>
      </div>
    </div>
  )
}
