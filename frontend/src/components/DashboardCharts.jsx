import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

const riskColors = {
  CRITICAL: '#ef4444',
  HIGH: '#f97316',
  MEDIUM: '#f59e0b',
  LOW: '#38bdf8',
}

const chartTooltip = {
  border: '1px solid #dbe0ec',
  borderRadius: 7,
  boxShadow: '0 6px 18px rgba(16, 31, 72, .1)',
  fontSize: 11,
}

function ChartPanel({ eyebrow, title, children, className = '' }) {
  return (
    <section className={`content-panel dashboard-chart-panel ${className}`}>
      <div className="panel-heading chart-heading">
        <div><span className="eyebrow">{eyebrow}</span><h2>{title}</h2></div>
      </div>
      <div className="chart-body">{children}</div>
    </section>
  )
}

function EmptyChart() {
  return <div className="chart-empty">No data available yet.</div>
}

export default function DashboardCharts({ metrics }) {
  const riskData = metrics.defectsByRiskLevel || []
  const scoreData = metrics.impactScoreDistribution || []
  const environmentData = metrics.defectsByEnvironment || []
  const serviceData = metrics.defectsByService || []
  const topDefects = metrics.topImpactDefects || []

  return (
    <div className="dashboard-charts-grid">
      <ChartPanel eyebrow="DISTRIBUTION" title="Defects by risk level">
        {riskData.some((point) => point.value > 0) ? <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie data={riskData} dataKey="value" nameKey="label" innerRadius={54} outerRadius={83} paddingAngle={3} stroke="none">
              {riskData.map((point) => <Cell key={point.label} fill={riskColors[point.label] || '#929daf'} />)}
            </Pie>
            <Tooltip contentStyle={chartTooltip} formatter={(value, name) => [value, name]} />
          </PieChart>
        </ResponsiveContainer> : <EmptyChart />}
        <div className="chart-legend">{riskData.map((point) => <span key={point.label}><i style={{ background: riskColors[point.label] || '#929daf' }} />{point.label}<strong>{point.value}</strong></span>)}</div>
      </ChartPanel>

      <ChartPanel eyebrow="SCORE BANDS" title="Deterministic impact score distribution">
        {scoreData.some((point) => point.value > 0) ? <ResponsiveContainer width="100%" height="100%"><BarChart data={scoreData} margin={{ top: 8, right: 8, left: -20, bottom: 0 }}>
          <CartesianGrid vertical={false} stroke="#e5e9f1" />
          <XAxis dataKey="label" tick={{ fontSize: 10, fill: '#79869b' }} axisLine={false} tickLine={false} />
          <YAxis allowDecimals={false} tick={{ fontSize: 10, fill: '#79869b' }} axisLine={false} tickLine={false} />
          <Tooltip contentStyle={chartTooltip} cursor={{ fill: '#f2f5f9' }} />
          <Bar dataKey="value" name="Defects" fill="#2563eb" radius={[5, 5, 0, 0]} barSize={34} />
        </BarChart></ResponsiveContainer> : <EmptyChart />}
      </ChartPanel>

      <ChartPanel eyebrow="ENVIRONMENT" title="Defects by environment">
        {environmentData.length ? <ResponsiveContainer width="100%" height="100%"><BarChart data={environmentData} layout="vertical" margin={{ top: 4, right: 8, left: 18, bottom: 4 }}>
          <CartesianGrid horizontal={false} stroke="#e5e9f1" />
          <XAxis type="number" allowDecimals={false} hide />
          <YAxis type="category" dataKey="label" width={80} tick={{ fontSize: 10, fill: '#5e6b81' }} axisLine={false} tickLine={false} />
          <Tooltip contentStyle={chartTooltip} cursor={{ fill: '#f2f5f9' }} />
          <Bar dataKey="value" name="Defects" fill="#0ea5e9" radius={[0, 5, 5, 0]} barSize={18} />
        </BarChart></ResponsiveContainer> : <EmptyChart />}
      </ChartPanel>

      <ChartPanel eyebrow="SERVICE LANDSCAPE" title="Defects by service">
        {serviceData.length ? <ResponsiveContainer width="100%" height="100%"><BarChart data={serviceData.slice(0, 8)} layout="vertical" margin={{ top: 4, right: 8, left: 18, bottom: 4 }}>
          <CartesianGrid horizontal={false} stroke="#e5e9f1" />
          <XAxis type="number" allowDecimals={false} hide />
          <YAxis type="category" dataKey="label" width={100} tick={{ fontSize: 9, fill: '#5e6b81' }} axisLine={false} tickLine={false} />
          <Tooltip contentStyle={chartTooltip} cursor={{ fill: '#f2f5f9' }} />
          <Bar dataKey="value" name="Defects" fill="#6366f1" radius={[0, 5, 5, 0]} barSize={18} />
        </BarChart></ResponsiveContainer> : <EmptyChart />}
      </ChartPanel>

      <ChartPanel eyebrow="TOP EXPOSURE" title="Top 5 by deterministic impact score" className="top-impact-chart">
        {topDefects.length ? <ResponsiveContainer width="100%" height="100%"><BarChart data={topDefects} margin={{ top: 8, right: 8, left: -20, bottom: 15 }}>
          <CartesianGrid vertical={false} stroke="#e5e9f1" />
          <XAxis dataKey="id" tickFormatter={(value) => `IQ-${String(value).padStart(4, '0')}`} tick={{ fontSize: 9, fill: '#79869b' }} axisLine={false} tickLine={false} />
          <YAxis domain={[0, 100]} tick={{ fontSize: 10, fill: '#79869b' }} axisLine={false} tickLine={false} />
          <Tooltip contentStyle={chartTooltip} formatter={(value, name) => [value, name]} labelFormatter={(value) => `IQ-${String(value).padStart(4, '0')}`} />
          <Bar dataKey="impactScore" name="Deterministic impact score" fill="#3b82f6" radius={[5, 5, 0, 0]} barSize={30} />
        </BarChart></ResponsiveContainer> : <EmptyChart />}
      </ChartPanel>
    </div>
  )
}
