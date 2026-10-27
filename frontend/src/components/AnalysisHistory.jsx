import { BarChart3, CheckCircle2, Clock3 } from 'lucide-react'
import { Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import RiskBadge from './RiskBadge.jsx'

function formatDate(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit' }).format(new Date(value))
}

export default function AnalysisHistory({ analyses = [], selectedId, onSelect }) {
  const trend = [...analyses].reverse().map((analysis, index) => ({
    name: index + 1,
    score: analysis.impactScore,
  }))

  return (
    <section className="content-panel analysis-history-panel">
      <div className="panel-heading">
        <div><span className="eyebrow">AUDIT TRAIL</span><h2>Analysis History</h2><p>Previous AI assessments are kept for comparison. Scores shown are AI-suggested; confidence is model self-reported (uncalibrated).</p></div>
        <Clock3 size={17} />
      </div>
      {analyses.length ? <>
        <div className="history-content">
          <div className="history-list">
            {analyses.map((analysis, index) => (
              <button className={`history-entry${analysis.id === selectedId ? ' selected' : ''}`} key={analysis.id} onClick={() => onSelect(analysis.id)}>
                <span className="history-entry-marker">{analysis.id === selectedId ? <CheckCircle2 size={15} /> : String(analyses.length - index).padStart(2, '0')}</span>
                <span className="history-entry-copy"><strong>{formatDate(analysis.createdAt)}</strong><span>{analysis.summary}</span></span>
                <span className="history-entry-score"><b>{analysis.impactScore}</b><small>/100</small></span>
                <RiskBadge level={analysis.riskLevel} />
                <RiskBadge priority={analysis.priority} />
                <span className="history-confidence" title="Model self-reported confidence (uncalibrated)" aria-label={`Model self-reported confidence (uncalibrated): ${analysis.confidence} out of 100`}>{analysis.confidence}/100 self-reported</span>
              </button>
            ))}
          </div>
          {analyses.length > 1 && <div className="history-trend"><div className="history-trend-heading"><span><BarChart3 size={14} />AI-suggested score trend</span><small>Oldest to newest</small></div><ResponsiveContainer width="100%" height={150}><LineChart data={trend} margin={{ top: 8, right: 10, left: -22, bottom: 0 }}><XAxis dataKey="name" hide /><YAxis domain={[0, 100]} tick={{ fontSize: 9, fill: '#79869b' }} axisLine={false} tickLine={false} /><Tooltip formatter={(value) => [`${value}/100`, 'AI-suggested score']} labelFormatter={(value) => `Analysis ${value}`} contentStyle={{ border: '1px solid #dbe0ec', borderRadius: 7, fontSize: 10 }} /><Line type="monotone" dataKey="score" stroke="#2563eb" strokeWidth={2.5} dot={{ r: 3, fill: '#fff', stroke: '#2563eb', strokeWidth: 2 }} activeDot={{ r: 5 }} /></LineChart></ResponsiveContainer></div>}
        </div>
      </> : <div className="history-empty">No analyses saved yet. Run Gemini analysis to create the first history record.</div>}
    </section>
  )
}
