import { useCallback, useEffect, useMemo, useState } from 'react'
import { FolderKanban, Plus, Search, SlidersHorizontal } from 'lucide-react'
import { Link } from 'react-router-dom'
import DefectTable from '../components/DefectTable.jsx'
import ErrorState from '../components/ErrorState.jsx'
import LoadingState from '../components/LoadingState.jsx'
import { defectApi, getApiErrorMessage, projectApi } from '../services/api.js'

const ALL = 'ALL'
const NO_PROJECT = 'NONE'

export default function DefectsPage() {
  const [defects, setDefects] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [riskFilter, setRiskFilter] = useState('ALL')
  const [environmentFilter, setEnvironmentFilter] = useState('ALL')
  const [projectFilter, setProjectFilter] = useState(ALL)
  const [projects, setProjects] = useState([])

  const loadDefects = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const rows = await defectApi.getDefects()
      const defectRows = Array.isArray(rows) ? rows : []
      const analyzedRows = await Promise.all(defectRows.map(async (defect) => {
        try {
          const history = await defectApi.getAnalysisHistory(defect.id)
          const latestImpact = Array.isArray(history) ? history[0] || null : null
          return { ...defect, latestImpact }
        } catch {
          return { ...defect, latestImpact: null }
        }
      }))
      setDefects(analyzedRows)
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Could not load defects.'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { loadDefects() }, [loadDefects])
  useEffect(() => {
    let active = true
    projectApi.list().then((rows) => { if (active) setProjects(rows) }).catch(() => {})
    return () => { active = false }
  }, [])

  // Defect counts per project for the filter labels, counted over the defects this user can see.
  const projectCounts = useMemo(() => defects.reduce((counts, defect) => {
    const key = defect.projectId == null ? NO_PROJECT : String(defect.projectId)
    counts[key] = (counts[key] || 0) + 1
    return counts
  }, {}), [defects])

  const filteredDefects = useMemo(() => defects.filter((defect) => {
    const needle = query.trim().toLowerCase()
    const matchesQuery = !needle || [defect.title, defect.service, defect.businessFunction, defect.description]
      .some((value) => String(value || '').toLowerCase().includes(needle))
    const matchesStatus = statusFilter === 'ALL' || defect.status === statusFilter
    const matchesRisk = riskFilter === 'ALL' || defect.baselineRiskLevel === riskFilter
    const matchesEnvironment = environmentFilter === 'ALL' || defect.environment === environmentFilter
    const matchesProject = projectFilter === ALL
      || (projectFilter === NO_PROJECT ? defect.projectId == null : String(defect.projectId) === projectFilter)
    return matchesQuery && matchesStatus && matchesRisk && matchesEnvironment && matchesProject
  }), [defects, query, statusFilter, riskFilter, environmentFilter, projectFilter])

  return (
    <div className="page-stack">
      <section className="welcome-row">
        <div><span className="eyebrow">WORKSPACE / DEFECTS</span><h2>Defect register</h2><p>Search and review reported defects across your services.</p></div>
        <Link className="button button-primary" to="/defects/new"><Plus size={16} />New defect</Link>
      </section>

      <section className="content-panel register-panel">
        <div className="register-toolbar">
          <div className="register-count"><strong>{defects.length}</strong><span>total records</span></div>
          <div className="filter-controls">
            <label className="select-filter"><FolderKanban size={15} /><select aria-label="Filter by project" value={projectFilter} onChange={(event) => setProjectFilter(event.target.value)}>
              <option value={ALL}>All projects</option>
              {projects.map((project) => <option key={project.id} value={String(project.id)}>{project.name} ({projectCounts[String(project.id)] || 0})</option>)}
              <option value={NO_PROJECT}>No project ({projectCounts[NO_PROJECT] || 0})</option>
            </select></label>
            <label className="search-field"><Search size={16} /><input aria-label="Search defects" placeholder="Search title, service, function…" value={query} onChange={(event) => setQuery(event.target.value)} /></label>
            <label className="select-filter"><SlidersHorizontal size={15} /><select aria-label="Filter by status" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}><option value="ALL">All statuses</option><option value="OPEN">Open</option><option value="IN_PROGRESS">In progress</option><option value="RESOLVED">Resolved</option><option value="CLOSED">Closed</option></select></label>
            <label className="select-filter"><select aria-label="Filter by risk" value={riskFilter} onChange={(event) => setRiskFilter(event.target.value)}><option value="ALL">All risk</option><option value="LOW">Low risk</option><option value="MEDIUM">Medium risk</option><option value="HIGH">High risk</option><option value="CRITICAL">Critical risk</option></select></label>
            <label className="select-filter"><select aria-label="Filter by environment" value={environmentFilter} onChange={(event) => setEnvironmentFilter(event.target.value)}><option value="ALL">All environments</option><option value="DEVELOPMENT">Development</option><option value="TEST">Test</option><option value="STAGING">Staging</option><option value="PRODUCTION">Production</option></select></label>
          </div>
        </div>
        {error ? <ErrorState message={error} onRetry={loadDefects} /> : loading ? <LoadingState label="Loading defects" /> : <DefectTable defects={filteredDefects} emptyMessage={query || statusFilter !== 'ALL' || riskFilter !== 'ALL' || environmentFilter !== 'ALL' || projectFilter !== ALL ? 'No defects match these filters.' : 'No defects have been logged yet.'} />}
      </section>
    </div>
  )
}
