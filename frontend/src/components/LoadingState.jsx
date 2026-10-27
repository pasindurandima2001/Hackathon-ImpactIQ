import { LoaderCircle } from 'lucide-react'

export default function LoadingState({ label = 'Loading data' }) {
  return (
    <div className="state-panel loading-state" role="status">
      <LoaderCircle className="spin" size={22} />
      <span>{label}</span>
    </div>
  )
}
