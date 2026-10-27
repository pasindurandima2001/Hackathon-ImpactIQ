const riskStyles = {
  LOW: 'risk-low',
  MEDIUM: 'risk-medium',
  HIGH: 'risk-high',
  CRITICAL: 'risk-critical',
}

const priorityStyles = {
  P3: 'priority-p3',
  P2: 'priority-p2',
  P1: 'priority-p1',
  P0: 'priority-p0',
}

export default function RiskBadge({ level, priority, children }) {
  if (priority || String(level || '').startsWith('P')) {
    const value = priority || level
    return <span className={`risk-badge priority-badge ${priorityStyles[value] || ''}`}>{value}</span>
  }
  const value = level || children || 'UNRATED'
  return <span className={`risk-badge ${riskStyles[value] || 'risk-neutral'}`}><i />{value}</span>
}
