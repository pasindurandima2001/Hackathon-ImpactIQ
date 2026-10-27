import { Activity, ArrowUpRight, CheckCircle2, Clock3, Gauge, Sparkles } from 'lucide-react'
import RiskBadge from './RiskBadge.jsx'

const dimensions = [
  ['User impact', 'userImpactScore'],
  ['Business impact', 'businessImpactScore'],
  ['Customer impact', 'customerImpactScore'],
  ['Technical impact', 'technicalImpactScore'],
]

// Current deterministic score, calculated by the backend from the defect's present field values.
function CurrentDeterministicScore({ defect }) {
  if (defect?.baselineScore == null) return null
  return (
    <span className="baseline-score" title="Calculated from the defect's current field values">
      <small>CURRENT DETERMINISTIC SCORE</small>
      <strong>{defect.baselineScore}<i>/100</i></strong>
      {(defect.baselineRiskLevel || defect.baselinePriority) && <span className="badge-row">{defect.baselineRiskLevel && <RiskBadge level={defect.baselineRiskLevel} />}{defect.baselinePriority && <RiskBadge priority={defect.baselinePriority} />}</span>}
    </span>
  )
}

export default function ImpactScoreCard({ analysis, defect, onAnalyze, analyzing = false, onReanalyze }) {
  if (!analysis) {
    return (
      <section className="analysis-empty gemini-analysis-prompt">
        <div className="analysis-empty-mark"><Sparkles size={22} /></div>
        <div><span className="eyebrow">GEMINI IMPACT REVIEW</span><h3>Ready to assess real-world impact</h3><p>No analysis has been run yet. Generate a structured AI analysis from this defect and its deterministic baseline score.</p></div>
        <CurrentDeterministicScore defect={defect} />
        <button className="button button-primary analyze-button" onClick={onAnalyze} disabled={analyzing}><Sparkles size={16} />{analyzing ? 'Analyzing defect impact...' : 'Analyze with Gemini AI'}</button>
      </section>
    )
  }

  // The circle, badges and breakdown show the current deterministic score; the AI-suggested score is not displayed.
  const score = Number(defect?.baselineScore ?? analysis.baselineScore ?? 0)
  const riskLevel = defect?.baselineRiskLevel || analysis.riskLevel
  const priority = defect?.baselinePriority || analysis.priority
  const breakdown = defect?.baselineBreakdown || analysis.scoreBreakdown
  const baselineChanged = defect?.baselineScore != null && Number(defect.baselineScore) !== Number(analysis.baselineScore || 0)
  const analyzedAt = analysis.createdAt
    ? new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(analysis.createdAt))
    : null
  return (
    <section className="impact-analysis-card">
      <div className="analysis-card-heading">
        <div><span className="eyebrow">GEMINI IMPACT REVIEW</span><h2>Impact analysis</h2>{analyzedAt && <span className="analysis-timestamp"><Clock3 size={12} />Analyzed {analyzedAt}</span>}</div>
        <div className="analysis-heading-actions"><span className="analysis-confidence" title="Reported by the model about its own output. Not a calibrated probability of correctness."><CheckCircle2 size={14} />Model self-reported confidence (uncalibrated): {analysis.confidence}/100</span><button className="button button-secondary button-small" onClick={onReanalyze} disabled={analyzing}><ArrowUpRight size={14} />{analyzing ? 'Running…' : 'Reanalyze'}</button></div>
      </div>
      <div className="analysis-score-row">
        <div className={`analysis-score-circle score-risk-${String(riskLevel || 'low').toLowerCase()}`} style={{ '--score': `${score * 3.6}deg` }} title="Calculated from the defect's current field values"><div><span className="impact-score-label">DETERMINISTIC SCORE</span><strong>{score}<small>/100</small></strong></div></div>
        <div className="analysis-score-copy"><div className="badge-row"><RiskBadge level={riskLevel} /><RiskBadge priority={priority} /></div><h3>{analysis.summary}</h3><p>{analysis.explanation}</p>{baselineChanged && <div className="score-comparison"><span className="score-delta" role="note">Defect changed since this analysis; reanalyze for an up-to-date AI summary.</span></div>}</div>
      </div>
      {breakdown && <div className="score-breakdown">{dimensions.map(([label, key]) => (
        <div className="breakdown-item" key={key}><div><span>{label}</span><strong>{breakdown[key] ?? '—'}</strong></div><div className="breakdown-track"><i style={{ width: `${breakdown[key] || 0}%` }} /></div></div>
      ))}</div>}
      <div className="analysis-lists">
        <div><h4>Recommended actions</h4><ul>{(analysis.recommendedActions || []).map((action, index) => <li key={`${action}-${index}`}><span className="action-number">{String(index + 1).padStart(2, '0')}</span>{action}</li>)}</ul></div>
        <div><h4>Affected areas</h4><div className="area-tags">{(analysis.affectedAreas || []).map((area) => <span key={area}>{area}</span>)}</div></div>
      </div>
    </section>
  )
}
