import { useState } from 'react'
import { CheckCircle2, ChevronDown, CircleHelp, ThumbsDown, ThumbsUp, XCircle } from 'lucide-react'
import { EvidenceChip } from './EvidenceReference.jsx'

const LIKELIHOOD_CLASS = { HIGH: 'risk-high', MEDIUM: 'risk-medium', LOW: 'risk-low' }

/** Latest CONFIRMED/REJECTED verdict recorded by a developer for this hypothesis, if any. */
function developerVerdict(feedback, rank) {
  return feedback.find((item) => item.hypothesisRank === rank && (item.verdict === 'CONFIRMED' || item.verdict === 'REJECTED'))
}

export default function HypothesisCard({ hypothesis, evidenceById, feedback = [], onFeedback, canReview }) {
  const [expanded, setExpanded] = useState(hypothesis.rank === 1)
  const [comment, setComment] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const verdict = developerVerdict(feedback, hypothesis.rank)

  async function send(verdictValue) {
    setBusy(true)
    setError('')
    try {
      await onFeedback({ hypothesisRank: hypothesis.rank, verdict: verdictValue, comment: comment.trim() || null })
      setComment('')
    } catch (requestError) {
      setError(requestError?.response?.data?.message || 'Feedback could not be saved.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <article className={`hypothesis-card${verdict ? ` verdict-${verdict.verdict.toLowerCase()}` : ''}`}>
      <button type="button" className="hypothesis-head" onClick={() => setExpanded((value) => !value)} aria-expanded={expanded}>
        <span className="hypothesis-rank">#{hypothesis.rank}</span>
        <span className="hypothesis-title">
          <strong>{hypothesis.title}</strong>
          <span className="badge-row">
            <span className={`risk-badge ${LIKELIHOOD_CLASS[hypothesis.likelihood] || 'risk-neutral'}`}><i />{hypothesis.likelihood} likelihood</span>
            <span className="confidence-pill" title="Model self-reported confidence (uncalibrated)">{hypothesis.confidence}/100 self-reported</span>
            {verdict ? (
              <span className={`verdict-pill verdict-pill-${verdict.verdict.toLowerCase()}`}>
                {verdict.verdict === 'CONFIRMED' ? <CheckCircle2 size={11} /> : <XCircle size={11} />}
                {verdict.verdict === 'CONFIRMED' ? 'Confirmed' : 'Rejected'} by {verdict.username}
              </span>
            ) : (
              <span className="verdict-pill verdict-pill-unconfirmed"><CircleHelp size={11} />Unconfirmed hypothesis</span>
            )}
          </span>
        </span>
        <ChevronDown size={16} className={`hypothesis-chevron${expanded ? ' open' : ''}`} />
      </button>
      {expanded && (
        <div className="hypothesis-body">
          {hypothesis.description && <p>{hypothesis.description}</p>}
          <div className="hypothesis-evidence">
            <div><span className="evidence-label supports">Supporting evidence</span><span className="chip-row">{hypothesis.supportingEvidenceIds.map((id) => <EvidenceChip key={id} id={id} evidenceById={evidenceById} tone="support" />)}</span></div>
            <div><span className="evidence-label conflicts">Conflicting evidence</span><span className="chip-row">{hypothesis.conflictingEvidenceIds.length ? hypothesis.conflictingEvidenceIds.map((id) => <EvidenceChip key={id} id={id} evidenceById={evidenceById} tone="conflict" />) : <small className="muted-note">None identified</small>}</span></div>
          </div>
          {hypothesis.reasoning && <div className="hypothesis-reasoning"><span className="eyebrow">REASONING</span><p>{hypothesis.reasoning}</p></div>}
          <div className="hypothesis-review">
            <input value={comment} onChange={(event) => setComment(event.target.value)} maxLength={2000} placeholder="Optional note for the team (e.g. how you verified it)" aria-label="Feedback comment" />
            <div className="hypothesis-review-actions">
              {canReview && <button type="button" className="button button-secondary button-small" disabled={busy} onClick={() => send('CONFIRMED')}><CheckCircle2 size={13} />Confirm</button>}
              {canReview && <button type="button" className="button button-danger-outline button-small" disabled={busy} onClick={() => send('REJECTED')}><XCircle size={13} />Reject</button>}
              <button type="button" className="icon-button" disabled={busy} onClick={() => send('HELPFUL')} title="Helpful" aria-label="Mark helpful"><ThumbsUp size={14} /></button>
              <button type="button" className="icon-button" disabled={busy} onClick={() => send('NOT_HELPFUL')} title="Not helpful" aria-label="Mark not helpful"><ThumbsDown size={14} /></button>
            </div>
          </div>
          {error && <div className="form-error" role="alert">{error}</div>}
        </div>
      )}
    </article>
  )
}
