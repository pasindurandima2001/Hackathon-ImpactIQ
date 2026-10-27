import { useCallback, useEffect, useState } from 'react'
import { Activity, AlertCircle, AlertTriangle, ArrowRight, Info, Plus } from 'lucide-react'
import { Link } from 'react-router-dom'
import DashboardCharts from '../components/DashboardCharts.jsx'
import ErrorState from '../components/ErrorState.jsx'
import HighestImpactTable from '../components/HighestImpactTable.jsx'
import LoadingState from '../components/LoadingState.jsx'
import StatCard from '../components/StatCard.jsx'
import { dashboardApi, getApiErrorMessage } from '../services/api.js'

export default function DashboardPage() {
  const [metrics, setMetrics] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadDefects = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setMetrics(await dashboardApi.getMetrics())
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Could not connect to the ImpactIQ API.'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { loadDefects() }, [loadDefects])

  return (
    <div className="page-stack">
      <section className="welcome-row">
        <div><span className="eyebrow">MONITOR / OVERVIEW</span><h2>Defect impact overview</h2><p>Track software issues by operational and customer exposure.</p></div>
        <Link className="button button-primary" to="/defects/new"><Plus size={16} />Log a defect</Link>
      </section>

      {error && <ErrorState message={error} onRetry={loadDefects} />}
      {loading ? <LoadingState label="Loading impact overview" /> : !error && metrics && (
        <>
          <section className="stats-grid stats-grid-five">
            <StatCard label="Total defects" value={metrics.totalDefects} detail="All tracked records" tone="green" icon={Activity} />
            <StatCard label="Critical defects" value={metrics.criticalDefects} detail="Deterministic risk: critical" tone="coral" icon={AlertTriangle} />
            <StatCard label="High defects" value={metrics.highDefects} detail="Deterministic risk: high" tone="orange" icon={AlertTriangle} />
            <StatCard label="Medium defects" value={metrics.mediumDefects} detail="Deterministic risk: medium" tone="amber" icon={AlertCircle} />
            <StatCard label="Low defects" value={metrics.lowDefects} detail="Deterministic risk: low" tone="sky" icon={Info} />
          </section>

          <section className="dashboard-tier-stats"><Link className="text-link" to="/defects">Open full register <ArrowRight size={15} /></Link></section>

          <DashboardCharts metrics={metrics} />
          <HighestImpactTable defects={metrics.topImpactDefects} />
        </>
      )}
    </div>
  )
}
