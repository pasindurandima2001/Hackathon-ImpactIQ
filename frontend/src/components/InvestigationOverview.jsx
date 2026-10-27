import { AlertOctagon, Bot, ClipboardCheck, FileSearch, RotateCcw } from 'lucide-react'
import { Link } from 'react-router-dom'
import { formatDate } from '../services/api.js'
import RiskBadge from './RiskBadge.jsx'
import StatCard from './StatCard.jsx'
import StatusBadge from './StatusBadge.jsx'

function ActivityList({ title, eyebrow, items, empty, icon: Icon, showRetry }) {
  return (
    <section className="content-panel activity-panel">
      <div className="panel-heading"><div><span className="eyebrow">{eyebrow}</span><h2>{title}</h2></div><Icon size={17} /></div>
      {items.length ? (
        <ul className="activity-list">
          {items.map((item) => (
            <li key={item.id}>
              <Link to={`/defects/${item.defectId}`} state={{ tab: 'investigation' }}>
                <span className="activity-main"><strong>{item.defectTitle || `Defect #${item.defectId}`}</strong>
                  <small>{item.topHypothesis ? `Top hypothesis (unconfirmed): ${item.topHypothesis}` : item.errorCategory ? `Error: ${item.errorCategory}` : `${item.hypothesisCount} hypotheses`}</small></span>
                <span className="activity-side">
                  <StatusBadge status={item.status} />
                  <small>{showRetry ? (item.retryEligible ? `Retry available · attempt ${item.attempt}` : `Retry limit reached · attempt ${item.attempt}`) : formatDate(item.createdAt)}</small>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      ) : <p className="history-empty">{empty}</p>}
    </section>
  )
}

/** Investigation metrics — all values come from persisted runs, evidence and defects. */
export default function InvestigationOverview({ metrics }) {
  if (!metrics) return null
  return (
    <>
      <section className="welcome-row section-break">
        <div><span className="eyebrow">AI INVESTIGATIONS</span><h2>Investigation operations</h2><p>Multi-agent runs, developer review queue and evidence coverage.</p></div>
      </section>
      <section className="stats-grid">
        <StatCard label="Investigations" value={metrics.totalInvestigations} detail={`${metrics.activeInvestigations} running · ${metrics.completedInvestigations} completed`} tone="green" icon={Bot} />
        <StatCard label="Pending reviews" value={metrics.pendingReviewCount} detail="Completed runs without developer feedback" tone="blue" icon={ClipboardCheck} />
        <StatCard label="Evidence coverage" value={`${metrics.evidenceCoveragePercent}%`} detail={`${metrics.defectsWithEvidence} defects with evidence · ${metrics.investigationCoveragePercent}% investigated`} tone="amber" icon={FileSearch} />
        <StatCard label="Unresolved high-risk" value={metrics.unresolvedHighRiskCount} detail={`${metrics.failedInvestigations} failed runs · ${metrics.retryEligibleInvestigations} retry eligible`} tone="coral" icon={AlertOctagon} />
      </section>
      <div className="activity-grid">
        <ActivityList title="Recent investigations" eyebrow="LATEST RUNS" items={metrics.recentInvestigations} empty="No investigations have run yet." icon={Bot} />
        <ActivityList title="Pending developer review" eyebrow="REVIEW QUEUE" items={metrics.pendingReviews} empty="Nothing waiting for review." icon={ClipboardCheck} />
        <ActivityList title="Failed investigations" eyebrow="RECOVERY" items={metrics.failedRuns} empty="No failed investigations." icon={RotateCcw} showRetry />
        <section className="content-panel activity-panel">
          <div className="panel-heading"><div><span className="eyebrow">DETERMINISTIC HIGH / CRITICAL</span><h2>Unresolved high-risk defects</h2></div><AlertOctagon size={17} /></div>
          {metrics.unresolvedHighRisk.length ? (
            <ul className="activity-list">
              {metrics.unresolvedHighRisk.map((defect) => (
                <li key={defect.id}><Link to={`/defects/${defect.id}`}>
                  <span className="activity-main"><strong>{defect.title}</strong><small>{defect.service} · {defect.affectedUsers.toLocaleString()} users</small></span>
                  <span className="activity-side"><span className="badge-row"><RiskBadge level={defect.riskLevel} /><RiskBadge priority={defect.priority} /></span><small>{defect.impactScore}/100</small></span>
                </Link></li>
              ))}
            </ul>
          ) : <p className="history-empty">No unresolved high-risk defects.</p>}
        </section>
      </div>
    </>
  )
}
