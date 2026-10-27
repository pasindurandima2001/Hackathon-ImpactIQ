import { ArrowUpRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import RiskBadge from './RiskBadge.jsx'

export default function HighestImpactTable({ defects = [] }) {
  return (
    <section className="content-panel highest-impact-panel">
      <div className="panel-heading">
        <div><span className="eyebrow">PRIORITY QUEUE</span><h2>Highest impact defects</h2><p>Top five records by deterministic impact score.</p></div>
      </div>
      {defects.length ? <div className="table-scroll"><table className="defect-table highest-impact-table"><thead><tr><th>Defect</th><th>Impact score (deterministic)</th><th>Risk</th><th>Priority</th><th>AI-suggested score</th><th>Affected users</th><th>Service</th><th>Environment</th><th /></tr></thead><tbody>{defects.map((defect) => <tr key={defect.id}><td><Link className="defect-title-link" to={`/defects/${defect.id}`}><span className="defect-id">IQ-{String(defect.id).padStart(4, '0')}</span><strong>{defect.title}</strong></Link></td><td><strong className="impact-table-score">{defect.impactScore}</strong><span className="score-denominator">/100</span></td><td><RiskBadge level={defect.riskLevel} /></td><td><RiskBadge priority={defect.priority} /></td><td className="number-cell">{defect.aiImpactScore ?? '—'}</td><td className="number-cell">{defect.affectedUsers.toLocaleString()}</td><td>{defect.service || 'UNSPECIFIED'}</td><td><span className={`environment-pill env-${(defect.environment || 'unknown').toLowerCase()}`}>{defect.environment || 'UNSPECIFIED'}</span></td><td><Link className="row-open" to={`/defects/${defect.id}`} aria-label={`Open ${defect.title}`}><ArrowUpRight size={16} /></Link></td></tr>)}</tbody></table></div> : <div className="table-empty">No scored defects available yet.</div>}
    </section>
  )
}
