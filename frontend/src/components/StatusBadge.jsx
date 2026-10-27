import { CheckCircle2, CircleDashed, CircleSlash, LoaderCircle, RotateCcw, XCircle } from 'lucide-react'

const styles = {
  QUEUED: ['status-chip-neutral', CircleDashed],
  PENDING: ['status-chip-neutral', CircleDashed],
  RUNNING: ['status-chip-blue', LoaderCircle],
  PROCESSING: ['status-chip-blue', LoaderCircle],
  COMPLETED: ['status-chip-green', CheckCircle2],
  SUCCEEDED: ['status-chip-green', CheckCircle2],
  INDEXED: ['status-chip-green', CheckCircle2],
  REUSED: ['status-chip-green', RotateCcw],
  PARTIAL: ['status-chip-amber', CircleSlash],
  SKIPPED: ['status-chip-neutral', CircleSlash],
  FAILED: ['status-chip-red', XCircle],
}

export default function StatusBadge({ status, label }) {
  const [className, Icon] = styles[status] || ['status-chip-neutral', CircleDashed]
  const spinning = status === 'RUNNING' || status === 'PROCESSING'
  return (
    <span className={`status-chip ${className}`}>
      <Icon size={12} className={spinning ? 'spin' : undefined} />
      {label || String(status || 'UNKNOWN').replaceAll('_', ' ')}
    </span>
  )
}
