import { useMemo, useState } from 'react'
import { AlertTriangle, Bot, Brain, CircleHelp, History, Layers3, MessageSquare, Play, RotateCcw, ShieldCheck, Sparkles } from 'lucide-react'
import { formatDate } from '../../services/api.js'
import ErrorState from '../ErrorState.jsx'
import LoadingState from '../LoadingState.jsx'
import RiskBadge from '../RiskBadge.jsx'
import StatusBadge from '../StatusBadge.jsx'
import { EvidenceList } from './EvidenceReference.jsx'
import FixRecommendation from './FixRecommendation.jsx'
import HypothesisCard from './HypothesisCard.jsx'
import InvestigationTimeline from './InvestigationTimeline.jsx'

const FINISHED_STEP = ['SUCCEEDED', 'REUSED']

/** While a run is active, shows each agent's result as soon as it finishes instead of waiting for the full report. */
function LiveProgress({ steps = [] }) {
  const done = steps.filter((step) => FINISHED_STEP.includes(step.status))
  const running = steps.filter((step) => step.status === 'RUNNING').map((step) => step.displayName)
  return (
    <div>
      <strong>Agents are working… {done.length}/{steps.length} done</strong>
      <p>{running.length ? `Now running: ${running.join(' + ')}` : 'Queued, starting shortly.'} You can leave this page; the run continues on the server.</p>
      {done.length > 0 && (
        <ul className="live-progress-list">
          {done.map((step) => <li key={step.id}><b>{step.displayName}</b>{step.summary ? `: ${step.summary}` : ''}</li>)}
        </ul>
      )}
    </div>
  )
}

function ListBlock({ title, items, icon: Icon, tone }) {
  if (!items?.length) return null
  return (
    <div className={`limitation-block${tone ? ` limitation-${tone}` : ''}`}>
      <h4>{Icon && <Icon size={13} />}{title}</h4>
      <ul>{items.map((item) => <li key={item}>{item}</li>)}</ul>
    </div>
  )
}

function GeneralFeedback({ onFeedback }) {
  const [comment, setComment] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')

  async function send(verdict) {
    setBusy(true)
    setMessage('')
    try {
      await onFeedback({ hypothesisRank: null, verdict, comment: comment.trim() || null })
      setComment('')
      setMessage('Thanks — feedback saved.')
    } catch (requestError) {
      setMessage(requestError?.response?.data?.message || 'Feedback could not be saved.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="general-feedback">
      <textarea value={comment} onChange={(event) => setComment(event.target.value)} rows={2} maxLength={2000} placeholder="Was this investigation useful? What was missing?" aria-label="Report feedback" />
      <div className="hypothesis-review-actions">
        <button type="button" className="button button-secondary button-small" disabled={busy} onClick={() => send('HELPFUL')}>Helpful</button>
        <button type="button" className="button button-secondary button-small" disabled={busy} onClick={() => send('NOT_HELPFUL')}>Not helpful</button>
        {message && <small className="muted-note">{message}</small>}
      </div>
    </div>
  )
}

export default function InvestigationWorkspace({ investigations, canEdit }) {
  const { runs, selected, loading, busy, error, active, reload, start, retry, select, giveFeedback } = investigations
  const report = selected?.report
  const summary = selected?.summary
  const evidenceById = useMemo(() => Object.fromEntries((report?.evidence || []).map((item) => [item.id, item])), [report])

  if (loading) return <LoadingState label="Loading investigations" />

  return (
    <div className="investigation-workspace">
      <section className="investigation-hero">
        <div className="investigation-hero-icon"><Bot size={20} /></div>
        <div className="investigation-hero-copy">
          <span className="eyebrow">MULTI-AGENT INVESTIGATION</span>
          <h3>{summary ? `Run #${summary.id} · attempt ${summary.attempt}` : 'No investigation yet'}</h3>
          <p>{summary
            ? `${summary.engine === 'ADK' ? 'Google ADK agents' : 'Gemini agents'} · ${summary.model || ''} · started ${formatDate(summary.createdAt)}`
            : 'Six agents gather project context, triage the defect, analyse evidence, rank root-cause hypotheses and recommend a fix — every claim cited to evidence.'}</p>
        </div>
        <div className="investigation-hero-actions">
          {summary && <StatusBadge status={summary.status} />}
          {summary?.retryEligible && canEdit && <button className="button button-secondary" onClick={() => retry(summary.id)} disabled={busy}><RotateCcw size={15} />Retry failed stages</button>}
          {canEdit && <button className="button button-primary" onClick={start} disabled={busy || active}><Play size={15} />{active ? 'Investigation running…' : summary ? 'Run new investigation' : 'Start investigation'}</button>}
        </div>
      </section>

      {error && <ErrorState message={error} onRetry={reload} />}

      {summary?.status === 'FAILED' && (
        <div className="form-error" role="alert"><strong>{summary.errorCategory}</strong>: {summary.errorMessage}{summary.retryEligible ? ' — successful stages will be reused on retry.' : ''}</div>
      )}

      {selected && (
        <div className="investigation-grid">
          <div className="investigation-main">
            {report ? (
              <>
                <section className="content-panel report-summary">
                  <div className="report-summary-score">
                    <span className="eyebrow">DETERMINISTIC IMPACT</span>
                    <strong>{report.impactScore.score}<small>/100</small></strong>
                    <span className="badge-row"><RiskBadge level={report.impactScore.riskLevel} /><RiskBadge priority={report.impactScore.priority} /></span>
                    <small className="muted-note">{report.impactScore.source}. AI cannot change it.</small>
                  </div>
                  <div className="report-summary-copy">
                    <span className="eyebrow">DEFECT SUMMARY · {String(report.defect.category).replaceAll('_', ' ')}</span>
                    <p>{report.defect.summary}</p>
                    <div className="report-facts">
                      <span><b>Severity</b>{report.defect.severity}</span>
                      <span><b>Priority</b>{report.defect.priority}</span>
                      <span><b>Project</b>{report.project.projectName || 'Not linked'}{report.project.verified && <ShieldCheck size={11} className="verified-icon" />}</span>
                      <span><b>Component</b>{report.project.componentName || 'Whole project'}</span>
                      {report.project.aiSuggestedComponent && <span><b>AI-identified area</b>{report.project.aiSuggestedComponent}</span>}
                      {report.triage?.advisoryTechnicalSeverity && <span title="Advisory only — not used for priority"><b>AI technical view</b>{report.triage.advisoryTechnicalSeverity} (advisory)</span>}
                      {report.mlSignal?.severity?.[0] && <span title={`${report.mlSignal.source}. Advisory only — not used for priority`}><b>ML model view</b>{report.mlSignal.severity[0].label} ({Math.round(report.mlSignal.severity[0].probability * 100)}%)</span>}
                    </div>
                  </div>
                </section>

                <section className="content-panel report-section">
                  <div className="panel-heading"><div><span className="eyebrow">ROOT CAUSE ANALYSIS</span><h2>Ranked hypotheses</h2><p>Potential causes only. A developer must confirm before any is treated as the root cause.</p></div><Brain size={17} /></div>
                  <div className="hypothesis-list">
                    {report.hypotheses.length ? report.hypotheses.map((hypothesis) => (
                      <HypothesisCard key={hypothesis.rank} hypothesis={hypothesis} evidenceById={evidenceById} feedback={selected.feedback} onFeedback={giveFeedback} canReview={canEdit} />
                    )) : <p className="muted-note">No evidence-backed hypotheses were produced in this run.</p>}
                  </div>
                </section>

                <section className="content-panel report-section">
                  <FixRecommendation fix={report.fix} />
                </section>

                <section className="content-panel report-section">
                  <div className="panel-heading"><div><span className="eyebrow">LIMITATIONS</span><h2>Missing evidence and uncertainty</h2></div><CircleHelp size={17} /></div>
                  <div className="limitations-grid">
                    <ListBlock title="Missing evidence" items={report.limitations.missingEvidence} icon={Layers3} />
                    <ListBlock title="Uncertainty" items={report.limitations.uncertainty} icon={CircleHelp} />
                    <ListBlock title="Server-side validation" items={report.limitations.validationWarnings} icon={ShieldCheck} tone="amber" />
                    <ListBlock title="Prompt-injection safeguards" items={report.limitations.promptInjectionWarnings} icon={AlertTriangle} tone="red" />
                    <ListBlock title="Failed or skipped stages" items={report.limitations.failedStages} icon={AlertTriangle} tone="red" />
                  </div>
                </section>

                <section className="content-panel report-section">
                  <div className="panel-heading"><div><span className="eyebrow">EVIDENCE ANALYSED · {report.limitations.retrievalMode ? `${report.limitations.retrievalMode} RETRIEVAL` : 'NO PROJECT RETRIEVAL'}</span><h2>Sources and findings</h2><p>D = defect report, E = attached evidence, K = project knowledge.</p></div><Layers3 size={17} /></div>
                  {report.findings?.length > 0 && (
                    <ul className="findings-list">
                      {report.findings.map((finding, index) => (
                        <li key={`${finding.evidenceId}-${index}`}><span className={`evidence-id kind-${finding.evidenceId.startsWith('K') ? 'project_knowledge' : 'evidence'}`}>{finding.evidenceId}</span><div><p>{finding.observation}</p><small>{finding.significance} significance{finding.location ? ` · ${finding.location}` : ''}</small></div></li>
                      ))}
                    </ul>
                  )}
                  <EvidenceList items={report.evidence} />
                </section>
              </>
            ) : (
              <section className="content-panel report-pending">
                {active ? <><Sparkles size={18} className="spin-slow" /><LiveProgress steps={selected.steps} /></>
                  : <><AlertTriangle size={18} /><div><strong>No report was produced</strong><p>Check the timeline for the failing stage.</p></div></>}
              </section>
            )}
          </div>

          <aside className="investigation-side">
            <section className="content-panel side-panel">
              <div className="panel-heading"><div><span className="eyebrow">EXECUTION</span><h2>Agent timeline</h2></div></div>
              <InvestigationTimeline steps={selected.steps} />
            </section>
            {report && (
              <section className="content-panel side-panel">
                <div className="panel-heading"><div><span className="eyebrow">DEVELOPER REVIEW</span><h2>Feedback</h2></div><MessageSquare size={16} /></div>
                <GeneralFeedback onFeedback={giveFeedback} />
                {selected.feedback.length > 0 && (
                  <ul className="feedback-list">
                    {selected.feedback.map((item) => (
                      <li key={item.id}><strong>{item.username}</strong> · {item.verdict.replaceAll('_', ' ').toLowerCase()}{item.hypothesisRank ? ` #${item.hypothesisRank}` : ''}<small>{formatDate(item.createdAt)}</small>{item.comment && <p>{item.comment}</p>}</li>
                    ))}
                  </ul>
                )}
              </section>
            )}
            {runs.length > 0 && (
              <section className="content-panel side-panel">
                <div className="panel-heading"><div><span className="eyebrow">HISTORY</span><h2>Investigation runs</h2></div><History size={16} /></div>
                <ul className="run-list">
                  {runs.map((run) => (
                    <li key={run.id}>
                      <button type="button" className={`run-row${run.id === summary?.id ? ' selected' : ''}`} onClick={() => select(run.id)}>
                        <span><strong>#{run.id}</strong> attempt {run.attempt}</span>
                        <StatusBadge status={run.status} />
                        <small>{run.topHypothesis || run.errorCategory || '—'}</small>
                        <small>{formatDate(run.createdAt)}{run.feedbackCount ? ` · ${run.feedbackCount} feedback` : ''}</small>
                      </button>
                    </li>
                  ))}
                </ul>
              </section>
            )}
          </aside>
        </div>
      )}
    </div>
  )
}
