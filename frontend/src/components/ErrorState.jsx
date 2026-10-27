import { AlertCircle, RotateCw } from 'lucide-react'

export default function ErrorState({ message = 'Unable to load data.', onRetry }) {
  return (
    <div className="state-panel error-state" role="alert">
      <span className="state-icon error-icon"><AlertCircle size={20} /></span>
      <div className="state-copy"><strong>Something went wrong</strong><span>{message}</span></div>
      {onRetry && <button className="button button-secondary button-small" onClick={onRetry}><RotateCw size={15} />Retry</button>}
    </div>
  )
}
