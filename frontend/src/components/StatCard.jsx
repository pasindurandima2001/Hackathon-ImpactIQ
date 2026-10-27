import { ArrowDownRight, ArrowUpRight } from 'lucide-react'

export default function StatCard({ label, value, detail, trend, tone = 'green', icon: Icon }) {
  const TrendIcon = trend?.direction === 'down' ? ArrowDownRight : ArrowUpRight
  return (
    <article className={`stat-card stat-${tone}`}>
      <div className="stat-card-top">
        <span className="stat-label">{label}</span>
        {Icon && <span className="stat-icon"><Icon size={17} /></span>}
      </div>
      <div className="stat-value">{value}</div>
      <div className="stat-foot">
        {trend && <span className={`stat-trend ${trend.direction || 'up'}`}><TrendIcon size={14} />{trend.value}</span>}
        <span>{detail}</span>
      </div>
    </article>
  )
}
