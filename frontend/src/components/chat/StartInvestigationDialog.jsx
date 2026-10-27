import { useEffect, useRef, useState } from 'react'
import { FlaskConical, X } from 'lucide-react'
import { Link } from 'react-router-dom'
import { getApiErrorMessage, investigationApi } from '../../services/api.js'

/**
 * Explicit confirmation before the existing investigation workflow runs. The chatbot never starts it on its
 * own: this dialog calls the existing endpoint, which re-checks the EDITOR role, the active-run lock and the
 * global queue limit.
 */
export default function StartInvestigationDialog({ action, defectLabel, onClose, onStarted, start = investigationApi.start }) {
  const [state, setState] = useState({ busy: false, error: '', started: null })
  const cancelRef = useRef(null)

  useEffect(() => {
    cancelRef.current?.focus()
    const onKey = (event) => { if (event.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  async function confirm() {
    setState({ busy: true, error: '', started: null })
    try {
      const run = await start(action.defectId)
      setState({ busy: false, error: '', started: run })
      onStarted?.(run)
    } catch (error) {
      setState({ busy: false, error: getApiErrorMessage(error, 'The investigation could not be started.'), started: null })
    }
  }

  return (
    <div className="chat-dialog-scrim" role="presentation" onClick={onClose}>
      <div className="chat-dialog" role="dialog" aria-modal="true" aria-labelledby="start-investigation-title" onClick={(event) => event.stopPropagation()}>
        <div className="chat-dialog-head">
          <span className="chat-dialog-icon"><FlaskConical size={17} /></span>
          <h3 id="start-investigation-title">Start investigation for {defectLabel}?</h3>
          <button type="button" className="icon-button" onClick={onClose} aria-label="Close"><X size={16} /></button>
        </div>
        {state.started ? (
          <>
            <p>The investigation was queued. It runs in the background and appears in the defect's Investigation tab.</p>
            <div className="chat-dialog-actions">
              <button type="button" className="button button-secondary" onClick={onClose}>Close</button>
              <Link className="button button-primary" to={`/defects/${action.defectId}`}>Open defect</Link>
            </div>
          </>
        ) : (
          <>
            <p>This runs the existing multi-agent investigation workflow on the attached defect. It reads the defect, its evidence and project knowledge, and <strong>uses AI quota</strong>. Hypotheses stay unconfirmed until a developer reviews them.</p>
            {state.error && <p className="chat-dialog-error" role="alert">{state.error}</p>}
            <div className="chat-dialog-actions">
              <button type="button" ref={cancelRef} className="button button-secondary" onClick={onClose} disabled={state.busy}>Cancel</button>
              <button type="button" className="button button-primary" onClick={confirm} disabled={state.busy || !action.enabled}>
                {state.busy ? 'Starting…' : 'Confirm and start'}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
