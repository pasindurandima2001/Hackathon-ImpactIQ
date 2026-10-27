export default function Tabs({ tabs, active, onChange, label = 'Sections' }) {
  return (
    <div className="tab-bar" role="tablist" aria-label={label}>
      {tabs.map(({ id, label: tabLabel, icon: Icon, badge }) => (
        <button
          key={id}
          type="button"
          role="tab"
          aria-selected={active === id}
          className={`tab-button${active === id ? ' active' : ''}`}
          onClick={() => onChange(id)}
        >
          {Icon && <Icon size={15} />}
          <span>{tabLabel}</span>
          {badge != null && badge !== '' && <span className="tab-badge">{badge}</span>}
        </button>
      ))}
    </div>
  )
}
