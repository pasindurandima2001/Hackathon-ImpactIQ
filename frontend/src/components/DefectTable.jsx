import { ArrowUpRight, ChevronRight, CircleDot } from 'lucide-react'
import { Link } from 'react-router-dom'
import RiskBadge from './RiskBadge.jsx'

function formatDate(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date(value))
}

export default function DefectTable({ defects = [], compact = false, emptyMessage = 'No defects match this view.' }) {
  if (!defects.length) {
    return <div className="table-empty"><CircleDot size={19} /><span>{emptyMessage}</span></div>
  }

  return (
    <div className="table-scroll">
      <table className={`defect-table${compact ? ' compact' : ''}`}>
        <thead>
          <tr>
            <th>ID</th>
            <th>Title</th>
            <th>Environment</th>
            <th>Service</th>
            <th>Affected users</th>
            <th>Status</th>
            <th>Impact score (deterministic)</th>
            <th>Risk level</th>
            <th>Priority</th>
            <th>AI-suggested score</th>
            <th>Created</th>
            <th aria-label="Open defect" />
          </tr>
        </thead>
        <tbody>
          {defects.map((defect) => (
            <tr key={defect.id}>
              <td>
                <Link className="defect-id" to={`/defects/${defect.id}`}>
                  IQ-{String(defect.id).padStart(4, '0')}
                </Link>
              </td>
              <td>
                <Link className="defect-title-link" to={`/defects/${defect.id}`}>
                  <strong>{defect.title}</strong>
                  <span className="cell-muted">{[defect.projectName, defect.businessFunction].filter(Boolean).join(' · ') || '—'}</span>
                </Link>
              </td>
              <td><span className={`environment-pill env-${(defect.environment || 'unknown').toLowerCase()}`}>{defect.environment || 'Not set'}</span></td>
              <td><span className="cell-primary">{defect.service || 'Unassigned'}</span></td>
              <td className="number-cell">{defect.affectedUsers ?? '—'}</td>
              <td><span className={`status-label status-${(defect.status || 'open').toLowerCase().replace('_', '-')}`}>{(defect.status || 'OPEN').replace('_', ' ')}</span></td>
              <td className="number-cell">{defect.baselineScore ?? '—'}</td>
              <td><RiskBadge level={defect.baselineRiskLevel || 'UNRATED'} /></td>
              <td>{defect.baselinePriority ? <RiskBadge priority={defect.baselinePriority} /> : '—'}</td>
              <td className="number-cell">{defect.latestImpact?.impactScore ?? '—'}</td>
              <td className="date-cell">{formatDate(defect.createdAt)}</td>
              <td><Link className="row-open" to={`/defects/${defect.id}`} aria-label={`Open ${defect.title}`}><ChevronRight size={17} /></Link></td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="table-bottom"><span>Showing {defects.length} {defects.length === 1 ? 'defect' : 'defects'}</span><span>Open record <ArrowUpRight size={13} /></span></div>
    </div>
  )
}
