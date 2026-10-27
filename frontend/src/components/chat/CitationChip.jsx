import { useState } from 'react'
import { AlertTriangle, BookOpenText, FileText, FlaskConical, MessageSquareText, Unlink } from 'lucide-react'
import { sourceLocation } from '../investigation/EvidenceReference.jsx'

const KIND_ICONS = { DEFECT_REPORT: MessageSquareText, PROJECT_KNOWLEDGE: BookOpenText, INVESTIGATION_FINDING: FlaskConical }

/** Adapts a stored chat citation to the shape the shared investigation helpers expect. */
export function toEvidenceItem(citation) {
  const source = citation.source || {}
  const filename = source.type === 'INVESTIGATION' ? `Investigation #${source.investigationId}` : source.filename
  return { ...citation, content: citation.excerpt || '', source: { ...source, filename } }
}

/**
 * Clickable evidence chip. Only server-verified citations are rendered; deleted sources say so. The popover
 * uses inline elements only, because chips sit inside Markdown paragraphs.
 */
export default function CitationChip({ id, citation }) {
  const [open, setOpen] = useState(false)
  if (!citation) return <span className="chat-citation-text">{id}</span>
  const unavailable = citation.available === false
  const item = toEvidenceItem(citation)
  const Icon = unavailable ? Unlink : KIND_ICONS[citation.kind] || FileText
  return (
    <span className="evidence-chip-wrap">
      <button
        type="button"
        className={`evidence-chip chat-citation${unavailable ? ' chat-citation-missing' : ''}`}
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        aria-label={`Evidence ${id}${unavailable ? ' (source no longer available)' : ''}`}
        title={unavailable ? 'Original source is no longer available' : citation.label}
      >
        {id}
        {citation.suspicious && <AlertTriangle size={10} />}
        {unavailable && <Unlink size={10} />}
      </button>
      {open && (
        <span className="evidence-source" role="dialog" aria-label={`Evidence ${id}`}>
          <span className="evidence-source-head">
            <Icon size={13} /><strong>{id} · {citation.label}</strong>
            <button type="button" className="text-link" onClick={() => setOpen(false)}>Close</button>
          </span>
          <span className="evidence-source-loc">{sourceLocation(item)}</span>
          {unavailable ? (
            <span className="evidence-source-warn">The original source is no longer available (it was deleted or replaced). The excerpt is not shown.</span>
          ) : (
            <>
              {citation.suspicious && <span className="evidence-source-warn"><AlertTriangle size={11} />Contains instruction-like text; treated strictly as data.</span>}
              <span className="chat-citation-excerpt">{citation.excerpt}</span>
            </>
          )}
        </span>
      )}
    </span>
  )
}
