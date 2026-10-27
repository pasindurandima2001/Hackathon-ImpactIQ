import { formatDate } from '../../services/api.js'
import StatusBadge from '../StatusBadge.jsx'

function duration(ms) {
  if (ms == null) return null
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`
}

/** Persisted stage-by-stage execution of the agent workflow. */
export default function InvestigationTimeline({ steps = [] }) {
  return (
    <ol className="agent-timeline">
      {steps.map((step) => (
        <li key={step.id} className={`agent-step step-${String(step.status).toLowerCase()}`}>
          <span className="agent-step-dot" />
          <div className="agent-step-body">
            <div className="agent-step-head">
              <strong>{step.displayName}</strong>
              <StatusBadge status={step.status} />
            </div>
            {step.summary && <p>{step.summary}</p>}
            {step.errorMessage && <p className="agent-step-error">{step.errorCategory ? `${step.errorCategory}: ` : ''}{step.errorMessage}</p>}
            <span className="agent-step-meta">
              {[duration(step.durationMs), step.attempts ? `${step.attempts} model call${step.attempts > 1 ? 's' : ''}` : null,
                step.completedAt ? formatDate(step.completedAt) : null].filter(Boolean).join(' · ')}
            </span>
          </div>
        </li>
      ))}
    </ol>
  )
}
