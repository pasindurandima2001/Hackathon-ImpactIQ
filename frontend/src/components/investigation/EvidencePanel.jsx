import { useCallback, useEffect, useState } from 'react'
import { AlertTriangle, BookOpenText, ClipboardPaste, FileText, FileUp, Plus, ShieldCheck, Trash2 } from 'lucide-react'
import { evidenceApi, formatDate, getApiErrorMessage } from '../../services/api.js'
import ErrorState from '../ErrorState.jsx'
import FilePicker from '../FilePicker.jsx'
import LoadingState from '../LoadingState.jsx'
import Tabs from '../Tabs.jsx'
import { sourceLocation } from './EvidenceReference.jsx'

const TYPES = ['STACK_TRACE', 'LOG', 'CODE', 'TEST_RESULT', 'CONFIGURATION', 'NOTE']

function AddEvidence({ defectId, onAdded }) {
  const [mode, setMode] = useState('paste')
  const [values, setValues] = useState({ type: 'STACK_TRACE', label: '', content: '' })
  const [file, setFile] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submit(event) {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      if (mode === 'paste') {
        await evidenceApi.add(defectId, { ...values, label: values.label.trim() })
        setValues({ type: values.type, label: '', content: '' })
      } else {
        await evidenceApi.upload(defectId, { file, type: values.type, label: values.label.trim() })
        setFile(null)
        setValues((current) => ({ ...current, label: '' }))
      }
      onAdded()
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Evidence could not be added.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="content-panel add-evidence" onSubmit={submit}>
      <div className="panel-heading"><div><span className="eyebrow">ADD EVIDENCE</span><h2>Logs, traces, code or test results</h2><p>Secrets are redacted before storage. Content is treated as untrusted data by every agent.</p></div><Plus size={17} /></div>
      <div className="add-evidence-body">
        <Tabs tabs={[{ id: 'paste', label: 'Paste', icon: ClipboardPaste }, { id: 'upload', label: 'Upload', icon: FileUp }]} active={mode} onChange={setMode} label="Evidence input" />
        <div className="form-grid">
          <label className="form-field"><span>Type</span><select value={values.type} onChange={(event) => setValues({ ...values, type: event.target.value })}>{TYPES.map((type) => <option key={type} value={type}>{type.replaceAll('_', ' ')}</option>)}</select></label>
          <label className="form-field"><span>Label{mode === 'paste' ? ' *' : ''}</span><input value={values.label} onChange={(event) => setValues({ ...values, label: event.target.value })} maxLength={255} required={mode === 'paste'} placeholder={mode === 'paste' ? 'e.g. API pod stack trace 14:02 UTC' : 'Defaults to the file name'} /></label>
        </div>
        {mode === 'paste'
          ? <label className="form-field full-field"><span>Content *</span><textarea className="mono-input" rows={8} value={values.content} onChange={(event) => setValues({ ...values, content: event.target.value })} maxLength={200000} required /></label>
          : <div className="full-field"><FilePicker file={file} onChange={setFile} disabled={busy} /></div>}
        {error && <div className="form-error" role="alert">{error}</div>}
        <div className="form-actions"><span>Max 200,000 characters per item, 50 items per defect.</span><button className="button button-primary" type="submit" disabled={busy || (mode === 'upload' && !file)}><Plus size={15} />{busy ? 'Saving…' : 'Add evidence'}</button></div>
      </div>
    </form>
  )
}

export default function EvidencePanel({ defectId, canEdit, report }) {
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setError('')
    try {
      setItems(await evidenceApi.list(defectId))
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Evidence could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }, [defectId])

  useEffect(() => { load() }, [load])

  async function remove(item) {
    if (!window.confirm(`Delete evidence "${item.label}"?`)) return
    try {
      await evidenceApi.remove(defectId, item.id)
      await load()
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Evidence could not be deleted.'))
    }
  }

  const knowledge = (report?.evidence || []).filter((item) => item.kind === 'PROJECT_KNOWLEDGE')

  return (
    <div className="evidence-workspace">
      {canEdit && <AddEvidence defectId={defectId} onAdded={load} />}
      <section className="content-panel">
        <div className="panel-heading"><div><span className="eyebrow">ATTACHED EVIDENCE</span><h2>{items.length} item{items.length === 1 ? '' : 's'}</h2><p>Referenced in reports as E1, E2… in this order.</p></div><FileText size={17} /></div>
        {error && <div className="panel-inset"><ErrorState message={error} onRetry={load} /></div>}
        {loading ? <LoadingState label="Loading evidence" /> : items.length === 0 ? (
          <p className="history-empty">No evidence attached yet. Stack traces, logs and failing test output make investigations far more precise.</p>
        ) : (
          <div className="report-evidence-list">
            {items.map((item, index) => (
              <details key={item.id} className="report-evidence-item">
                <summary>
                  <span className="evidence-id kind-evidence">E{index + 1}</span>
                  <span className="report-evidence-label"><strong>{item.label}</strong><small>{item.type.replaceAll('_', ' ')} · {item.sourceFilename || 'pasted'} · {item.lineCount} lines · {formatDate(item.createdAt)}</small></span>
                  {item.redactionCount > 0 && <span className="risk-badge risk-low" title="Secrets removed before storage"><ShieldCheck size={10} />{item.redactionCount} redacted</span>}
                  {item.suspicious && <span className="risk-badge risk-high" title="Contains instruction-like text"><AlertTriangle size={10} />Suspicious</span>}
                  {canEdit && <button type="button" className="icon-button" onClick={(event) => { event.preventDefault(); remove(item) }} aria-label={`Delete ${item.label}`}><Trash2 size={14} /></button>}
                </summary>
                <pre className="evidence-content">{item.content}</pre>
                <small className="muted-note">SHA-256 {item.contentSha256.slice(0, 16)}…</small>
              </details>
            ))}
          </div>
        )}
      </section>
      <section className="content-panel">
        <div className="panel-heading"><div><span className="eyebrow">PROJECT KNOWLEDGE · LATEST INVESTIGATION</span><h2>Retrieved sources</h2><p>Passages retrieved from this defect's project only, with document version and line/page references.</p></div><BookOpenText size={17} /></div>
        {knowledge.length === 0 ? <p className="history-empty">{report ? 'No project knowledge was retrieved for the latest investigation.' : 'Run an investigation to retrieve project knowledge.'}</p> : (
          <div className="report-evidence-list">
            {knowledge.map((item) => (
              <details key={item.id} className="report-evidence-item">
                <summary><span className="evidence-id kind-project_knowledge">{item.id}</span><span className="report-evidence-label"><strong>{item.label}</strong><small>{sourceLocation(item)}</small></span></summary>
                <pre className="evidence-content">{item.content}</pre>
              </details>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
