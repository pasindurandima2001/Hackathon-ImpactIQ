import { useCallback, useEffect, useState } from 'react'
import { Activity, ArrowLeft, Bot, CalendarDays, Edit3, FileText, FolderKanban, History, LayoutDashboard, Trash2, Users } from 'lucide-react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import AnalysisHistory from '../components/AnalysisHistory.jsx'
import DefectForm from '../components/DefectForm.jsx'
import ErrorState from '../components/ErrorState.jsx'
import ImpactScoreCard from '../components/ImpactScoreCard.jsx'
import EvidencePanel from '../components/investigation/EvidencePanel.jsx'
import InvestigationWorkspace from '../components/investigation/InvestigationWorkspace.jsx'
import LoadingState from '../components/LoadingState.jsx'
import RiskBadge from '../components/RiskBadge.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import Tabs from '../components/Tabs.jsx'
import useInvestigations from '../hooks/useInvestigations.js'
import { defectApi, formatDate, getApiErrorMessage } from '../services/api.js'

export default function DefectDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const [tab, setTab] = useState(location.state?.tab || 'overview')
  const [defect, setDefect] = useState(null)
  const [analysis, setAnalysis] = useState(null)
  const [analyses, setAnalyses] = useState([])
  const [selectedAnalysisId, setSelectedAnalysisId] = useState(null)
  const [loading, setLoading] = useState(true)
  const [analyzing, setAnalyzing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState(false)
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')
  const investigations = useInvestigations(id)

  const loadData = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const record = await defectApi.getDefect(id)
      setDefect(record)
      const history = await defectApi.getAnalysisHistory(id).catch(() => [])
      setAnalyses(history)
      const latest = history[0] || null
      setAnalysis(latest)
      setSelectedAnalysisId(latest?.id || null)
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Could not load this defect.'))
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => { loadData() }, [loadData])

  async function runAnalysis(reanalyze = false) {
    setAnalyzing(true)
    setActionError('')
    try {
      const result = reanalyze ? await defectApi.reanalyzeDefect(id) : await defectApi.analyzeDefect(id)
      setAnalysis(result)
      setSelectedAnalysisId(result.id)
      setAnalyses((current) => [result, ...current.filter((item) => item.id !== result.id)])
    } catch (requestError) {
      const apiMessage = requestError?.response?.data?.message
      const message = apiMessage === 'Gemini API key is not configured'
        ? 'Gemini API key is missing. Set GEMINI_API_KEY or add gemini.api-key to backend/.env, restart the backend, and retry.'
        : apiMessage || getApiErrorMessage(requestError, 'Impact analysis could not be completed.')
      setActionError(message)
    } finally {
      setAnalyzing(false)
    }
  }

  async function updateDefect(payload) {
    setSaving(true)
    setActionError('')
    try {
      const updated = await defectApi.updateDefect(id, payload)
      setDefect(updated)
      setEditing(false)
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, 'Could not update this defect.')
      setActionError(message)
      throw new Error(message)
    } finally {
      setSaving(false)
    }
  }

  async function deleteDefect() {
    if (!window.confirm('Delete this defect, its evidence and all investigations? This cannot be undone.')) return
    try {
      await defectApi.deleteDefect(id)
      navigate('/defects')
    } catch (requestError) {
      setActionError(getApiErrorMessage(requestError, 'Could not delete this defect.'))
    }
  }

  if (loading) return <LoadingState label="Loading defect record" />
  if (error) return <div className="page-stack"><ErrorState message={error} onRetry={loadData} /></div>
  if (!defect) return null

  // The server only returns a defect to its reporter or an administrator, and both may change it.
  const canEdit = Boolean(defect)
  const latestRun = investigations.runs[0]
  const tabs = [
    { id: 'overview', label: 'Overview', icon: LayoutDashboard },
    { id: 'investigation', label: 'Investigation', icon: Bot, badge: latestRun ? latestRun.status.slice(0, 1) + latestRun.status.slice(1).toLowerCase() : null },
    { id: 'evidence', label: 'Evidence', icon: FileText },
    { id: 'history', label: 'History', icon: History, badge: analyses.length + investigations.runs.length || null },
  ]

  return (
    <div className="page-stack">
      <Link className="back-link" to="/defects"><ArrowLeft size={15} />Back to register</Link>
      <section className="detail-heading">
        <div className="detail-title-wrap">
          <span className="eyebrow">DEFECT / IQ-{String(defect.id).padStart(4, '0')}</span>
          <h2>{defect.title}</h2>
          <p className="detail-description">{defect.description}</p>
          <div className="detail-meta">
            <span><Users size={14} />{(defect.affectedUsers ?? 0).toLocaleString()} affected users</span>
            <span><CalendarDays size={14} />Created {formatDate(defect.createdAt)}</span>
            {defect.projectName && <span><FolderKanban size={14} />{defect.projectName}{defect.componentName ? ` / ${defect.componentName}` : ''}</span>}
            <span className={`environment-pill env-${(defect.environment || '').toLowerCase()}`}>{defect.environment || 'No environment'}</span>
            <span className={`status-label status-${(defect.status || 'open').toLowerCase().replace('_', '-')}`}>{(defect.status || 'OPEN').replace('_', ' ')}</span>
            <RiskBadge level={defect.baselineRiskLevel} /><RiskBadge priority={defect.baselinePriority} />
          </div>
        </div>
        {canEdit && <div className="detail-actions"><button className="button button-secondary" onClick={() => setEditing((value) => !value)}><Edit3 size={15} />{editing ? 'Close editor' : 'Edit defect'}</button><button className="button button-danger-outline" onClick={deleteDefect}><Trash2 size={15} />Delete</button></div>}
      </section>

      {location.state?.successMessage && <div className="success-feedback" role="status">{location.state.successMessage}</div>}
      {actionError && <div className="form-error" role="alert">{actionError}</div>}
      {editing && <section className="content-panel form-panel"><div className="panel-heading"><div><span className="eyebrow">RECORD MANAGEMENT</span><h2>Edit defect</h2></div></div><DefectForm key={defect.id} initialValues={defect} onSubmit={updateDefect} loading={saving} submitLabel="Save changes" /></section>}

      <Tabs tabs={tabs} active={tab} onChange={setTab} label="Defect workspace" />

      {tab === 'overview' && (
        <>
          {latestRun && (
            <button type="button" className="overview-investigation" onClick={() => setTab('investigation')}>
              <Bot size={17} />
              <span><strong>Latest investigation #{latestRun.id}</strong><small>{latestRun.topHypothesis ? `Top hypothesis (unconfirmed): ${latestRun.topHypothesis}` : latestRun.errorMessage || 'Open the investigation workspace'}</small></span>
              <StatusBadge status={latestRun.status} />
            </button>
          )}
          <ImpactScoreCard analysis={analysis} defect={defect} onAnalyze={() => runAnalysis(false)} onReanalyze={() => runAnalysis(true)} analyzing={analyzing} />
          <div className="detail-facts-grid">
            <section className="content-panel fact-panel"><div className="panel-heading"><div><span className="eyebrow">SYSTEM CONTEXT</span><h2>Impact signals</h2></div><Activity size={17} /></div><div className="fact-grid"><div><span>Service</span><strong>{defect.service || 'Not specified'}</strong></div><div><span>Business function</span><strong>{defect.businessFunction || 'Not specified'}</strong></div><div><span>Business criticality</span><RiskBadge level={defect.businessCriticality} /></div><div><span>Customer impact</span><RiskBadge level={defect.customerImpact} /></div><div><span>Technical impact</span><RiskBadge level={defect.technicalImpact} /></div></div></section>
            <section className="content-panel lifecycle-panel"><span className="eyebrow">RECORD LIFECYCLE</span><h2>Tracking</h2><div className="lifecycle-line"><i /><div><strong>Last updated</strong><span>{formatDate(defect.updatedAt)}</span></div></div><div className="lifecycle-line"><i /><div><strong>Current status</strong><span>{(defect.status || 'OPEN').replace('_', ' ')}</span></div></div><div className="lifecycle-line"><i /><div><strong>Deterministic score</strong><span>{defect.baselineScore}/100 · {defect.baselinePriority}</span></div></div></section>
          </div>
        </>
      )}

      {tab === 'investigation' && <InvestigationWorkspace investigations={investigations} canEdit={canEdit} />}
      {tab === 'evidence' && <EvidencePanel defectId={id} canEdit={canEdit} report={investigations.selected?.report} />}
      {tab === 'history' && (
        <>
          <AnalysisHistory analyses={analyses} selectedId={selectedAnalysisId} onSelect={(analysisId) => {
            const selected = analyses.find((item) => item.id === analysisId)
            if (selected) { setAnalysis(selected); setSelectedAnalysisId(selected.id); setTab('overview') }
          }} />
          <section className="content-panel">
            <div className="panel-heading"><div><span className="eyebrow">AUDIT TRAIL</span><h2>Investigation history</h2><p>Every run, retry and its outcome is retained.</p></div><History size={17} /></div>
            {investigations.runs.length ? (
              <div className="table-scroll"><table className="defect-table history-table"><thead><tr><th>Run</th><th>Status</th><th>Attempt</th><th>Engine</th><th>Top hypothesis</th><th>Feedback</th><th>Started</th><th>Completed</th></tr></thead><tbody>
                {investigations.runs.map((run) => (
                  <tr key={run.id} onClick={() => { investigations.select(run.id); setTab('investigation') }} className="clickable-row">
                    <td>#{run.id}{run.parentInvestigationId ? <small className="cell-muted">retry of #{run.parentInvestigationId}</small> : null}</td>
                    <td><StatusBadge status={run.status} /></td><td>{run.attempt}</td><td>{run.engine}</td>
                    <td>{run.topHypothesis || run.errorCategory || '—'}</td><td>{run.feedbackCount}</td>
                    <td className="date-cell">{formatDate(run.createdAt)}</td><td className="date-cell">{formatDate(run.completedAt)}</td>
                  </tr>
                ))}
              </tbody></table></div>
            ) : <p className="history-empty">No investigations yet.</p>}
          </section>
        </>
      )}
    </div>
  )
}
