import { useState } from 'react'
import { AlertTriangle, BookOpenText, FileText, MessageSquareText } from 'lucide-react'

const KIND_ICONS = { DEFECT_REPORT: MessageSquareText, PROJECT_KNOWLEDGE: BookOpenText }

export function sourceLocation(item) {
  const source = item?.source || {}
  const lines = source.startLine ? `lines ${source.startLine}${source.endLine && source.endLine !== source.startLine ? `–${source.endLine}` : ''}` : null
  if (source.type === 'DEFECT') return 'Defect report (title and description)'
  if (source.type === 'DOCUMENT_CHUNK') {
    return [source.filename, source.versionNumber ? `v${source.versionNumber}` : null,
      source.pageNumber ? `page ${source.pageNumber}` : null, lines,
      source.retrievalScore != null ? `relevance ${source.retrievalScore}` : null].filter(Boolean).join(' · ')
  }
  return [source.filename || 'Pasted evidence', lines].filter(Boolean).join(' · ')
}

/** Clickable citation chip; expands to show the exact source location and the excerpt the agents saw. */
export function EvidenceChip({ id, evidenceById, tone }) {
  const [open, setOpen] = useState(false)
  const item = evidenceById?.[id]
  return (
    <span className="evidence-chip-wrap">
      <button type="button" className={`evidence-chip${tone ? ` evidence-chip-${tone}` : ''}`} onClick={() => setOpen((value) => !value)} aria-expanded={open} title={item ? sourceLocation(item) : 'Unknown evidence'}>
        {id}{item?.suspicious && <AlertTriangle size={10} />}
      </button>
      {open && item && <EvidenceSource item={item} onClose={() => setOpen(false)} />}
    </span>
  )
}

export function EvidenceSource({ item, onClose }) {
  const Icon = KIND_ICONS[item.kind] || FileText
  return (
    <span className="evidence-source" role="dialog" aria-label={`Evidence ${item.id}`}>
      <span className="evidence-source-head">
        <Icon size={13} /><strong>{item.id} · {item.label}</strong>
        {onClose && <button type="button" className="text-link" onClick={onClose}>Close</button>}
      </span>
      <span className="evidence-source-loc">{sourceLocation(item)}{item.truncated ? ' · excerpt truncated' : ''}</span>
      {item.suspicious && <span className="evidence-source-warn"><AlertTriangle size={11} />Contains instruction-like text; treated strictly as data.</span>}
      <pre>{item.content?.slice(0, 2500)}{item.content?.length > 2500 ? '\n…' : ''}</pre>
    </span>
  )
}

export function EvidenceList({ items }) {
  if (!items?.length) return <p className="muted-note">No evidence was available to the agents.</p>
  return (
    <div className="report-evidence-list">
      {items.map((item) => (
        <details key={item.id} className="report-evidence-item">
          <summary>
            <span className={`evidence-id kind-${String(item.kind).toLowerCase()}`}>{item.id}</span>
            <span className="report-evidence-label"><strong>{item.label}</strong><small>{sourceLocation(item)}</small></span>
            {item.suspicious && <span className="risk-badge risk-high"><AlertTriangle size={10} />Suspicious</span>}
          </summary>
          <pre className="evidence-content">{item.content}</pre>
        </details>
      ))}
    </div>
  )
}
