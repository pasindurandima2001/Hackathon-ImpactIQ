import { useState } from 'react'
import { AlertTriangle, ArrowLeft, ClipboardPaste, ExternalLink, FilePlus2, FileUp, Link2, ListChecks, Pencil, RotateCcw, Sparkles, Zap } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import DefectForm from '../components/DefectForm.jsx'
import FilePicker from '../components/FilePicker.jsx'
import ProjectScopeFields from '../components/ProjectScopeFields.jsx'
import Tabs from '../components/Tabs.jsx'
import { defectApi, draftApi, evidenceApi, getApiErrorMessage } from '../services/api.js'

const MODES = [
  { id: 'QUICK', label: 'Quick add', icon: Zap },
  { id: 'PASTE_ERROR', label: 'Paste error', icon: ClipboardPaste },
  { id: 'UPLOAD', label: 'Upload file', icon: FileUp },
  { id: 'AZURE_DEVOPS', label: 'Azure DevOps', icon: Link2 },
  { id: 'MANUAL', label: 'Full form', icon: Pencil },
]

// Accepts the edit link of a work item or a board/backlog link with ?workitem=ID
const ADO_LINK = /^https:\/\/([a-z0-9-]+\.visualstudio\.com|dev\.azure\.com)\/.+(_workitems\/edit\/\d+|[?&]workitem=\d+)/i

const MODE_COPY = {
  QUICK: ['Describe the problem in a sentence or two', 'e.g. Since this morning, customers paying with saved cards get a spinner and the order never completes.'],
  PASTE_ERROR: ['Paste a stack trace, log excerpt or error output', 'java.lang.NullPointerException: ...\n\tat com.shop.PaymentService.charge(PaymentService.java:42)'],
}

const DRAFT_FIELDS = ['title', 'description', 'environment', 'service', 'businessFunction', 'affectedUsers',
  'businessCriticality', 'customerImpact', 'technicalImpact']

/** Converts a draft into form values; only non-empty suggestions are applied and marked as AI-suggested. */
function draftToForm(draft, scope) {
  const values = { projectId: scope.projectId, componentId: scope.componentId }
  const aiFields = new Set()
  DRAFT_FIELDS.forEach((field) => {
    const value = draft[field]
    if (value !== null && value !== undefined && value !== '') {
      values[field] = value
      if (draft.source === 'AI' || field === 'title' || field === 'description') aiFields.add(field)
    }
  })
  return { values, aiFields }
}

export default function DefectFormPage() {
  const navigate = useNavigate()
  const [mode, setMode] = useState('QUICK')
  const [text, setText] = useState('')
  const [note, setNote] = useState('')
  const [file, setFile] = useState(null)
  const [adoUrl, setAdoUrl] = useState('')
  const [scope, setScope] = useState({ projectId: null, componentId: null })
  const [draft, setDraft] = useState(null)
  const [attachEvidence, setAttachEvidence] = useState(true)
  const [drafting, setDrafting] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  async function generateDraft(event) {
    event.preventDefault()
    setError('')
    setDrafting(true)
    try {
      let result
      if (mode === 'UPLOAD') result = await draftApi.draftFromFile({ file, note, ...scope })
      else if (mode === 'AZURE_DEVOPS') result = await draftApi.draftFromAzureDevOps({ url: adoUrl.trim(), ...scope })
      else result = await draftApi.draft({ mode, text, ...scope })
      setDraft(result)
      setAttachEvidence(Boolean(result.evidence))
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'The draft could not be generated.'))
    } finally {
      setDrafting(false)
    }
  }

  async function createDefect(payload) {
    setSaving(true)
    try {
      const created = await defectApi.createDefect(payload)
      let message = 'Defect created successfully.'
      if (draft?.evidence && attachEvidence) {
        try {
          await evidenceApi.add(created.id, {
            type: draft.evidence.type,
            label: draft.evidence.label.slice(0, 255),
            content: draft.evidence.content,
          })
          message = 'Defect created and the source material was attached as evidence.'
        } catch (evidenceError) {
          message = `Defect created, but the evidence could not be attached: ${getApiErrorMessage(evidenceError)}`
        }
      }
      navigate(`/defects/${created.id}`, { state: { successMessage: message } })
    } catch (requestError) {
      throw new Error(getApiErrorMessage(requestError, 'Could not create defect.'))
    } finally {
      setSaving(false)
    }
  }

  function changeMode(next) {
    setMode(next)
    setDraft(null)
    setError('')
  }

  let canDraft = text.trim().length >= 10
  if (mode === 'UPLOAD') canDraft = Boolean(file)
  else if (mode === 'AZURE_DEVOPS') canDraft = ADO_LINK.test(adoUrl.trim())
  const external = draft?.externalItem
  const reviewing = draft && mode !== 'MANUAL'
  const form = reviewing ? draftToForm(draft, scope) : null

  return (
    <div className="page-stack narrow-page">
      <Link className="back-link" to="/defects"><ArrowLeft size={15} />Back to register</Link>
      <section className="form-page-heading"><div className="form-heading-icon"><FilePlus2 size={20} /></div><div><span className="eyebrow">NEW RECORD / DEFECT INTAKE</span><h2>Log a software defect</h2><p>Start from a sentence, an error or a file. AI drafts the record; nothing is saved until you confirm.</p></div></section>

      <Tabs tabs={MODES} active={mode} onChange={changeMode} label="Intake mode" />

      {mode === 'MANUAL' && (
        <section className="content-panel form-panel"><DefectForm onSubmit={createDefect} loading={saving} submitLabel="Create defect" /></section>
      )}

      {mode !== 'MANUAL' && !reviewing && (
        <form className="content-panel intake-panel" onSubmit={generateDraft}>
          <div className="form-section-heading"><span className="section-index">01</span><div><h2>{mode === 'UPLOAD' ? 'Upload a log, trace or report' : mode === 'AZURE_DEVOPS' ? 'Paste an Azure DevOps work item link' : MODE_COPY[mode][0]}</h2><p>{mode === 'AZURE_DEVOPS' ? 'ImpactIQ reads the work item, its repro steps and comments, then drafts the defect. Secrets are redacted before anything is sent to the model.' : 'Secrets such as passwords, tokens and keys are redacted before anything is sent to the model.'}</p></div></div>
          {mode === 'AZURE_DEVOPS' ? (
            <label className="form-field full-field">
              <span>Work item link <b>*</b></span>
              <input id="ado-url" type="url" value={adoUrl} onChange={(event) => setAdoUrl(event.target.value)} maxLength={2048} placeholder="https://dev.azure.com/your-org/your-project/_workitems/edit/12345" required />
              {adoUrl.trim() && !ADO_LINK.test(adoUrl.trim()) && <small className="field-hint">Open the work item in Azure DevOps and copy the link from the browser address bar.</small>}
            </label>
          ) : mode === 'UPLOAD' ? (
            <>
              <FilePicker file={file} onChange={setFile} label="Drop a file or browse" disabled={drafting} />
              <label className="form-field full-field"><span>Note for the drafter (optional)</span><input value={note} onChange={(event) => setNote(event.target.value)} maxLength={500} placeholder="e.g. Seen after the 4.2 release on Android" /></label>
            </>
          ) : (
            <label className="form-field full-field">
              <span>{mode === 'QUICK' ? 'Problem summary' : 'Error output'} <b>*</b></span>
              <textarea className={mode === 'PASTE_ERROR' ? 'mono-input' : undefined} value={text} onChange={(event) => setText(event.target.value)} rows={mode === 'QUICK' ? 4 : 10} maxLength={50000} placeholder={MODE_COPY[mode][1]} required />
            </label>
          )}
          <div className="form-grid"><ProjectScopeFields projectId={scope.projectId} componentId={scope.componentId} onChange={setScope} /></div>
          {error && <div className="form-error" role="alert">{error}</div>}
          <div className="form-actions">
            <span>The draft is editable. You confirm every field before the defect is saved.</span>
            <button className="button button-primary" type="submit" disabled={!canDraft || drafting}><Sparkles size={16} />{drafting ? (mode === 'AZURE_DEVOPS' ? 'Importing and drafting…' : 'Drafting with AI…') : (mode === 'AZURE_DEVOPS' ? 'Import and draft' : 'Generate AI draft')}</button>
          </div>
        </form>
      )}

      {reviewing && (
        <>
          <section className={`draft-banner${draft.source === 'AI' ? '' : ' draft-banner-warning'}`}>
            <div className="draft-banner-icon">{draft.source === 'AI' ? <Sparkles size={18} /> : <AlertTriangle size={18} />}</div>
            <div>
              <span className="eyebrow">{draft.source === 'AI' ? `AI DRAFT · ${draft.engine || 'GEMINI'}` : 'BASIC DRAFT (AI UNAVAILABLE)'}</span>
              <h3>Review the suggested defect before saving</h3>
              {external && (
                <p className="draft-meta">
                  Imported from <a href={external.url} target="_blank" rel="noopener noreferrer"><strong>{external.workItemType} #{external.id}</strong> <ExternalLink size={12} /></a>
                  {[external.state && `State: ${external.state}`, external.severity && `Severity: ${external.severity}`,
                    external.priority && `Priority: ${external.priority}`, external.areaPath && `Area: ${external.areaPath}`,
                    `${external.commentCount} comment${external.commentCount === 1 ? '' : 's'}`,
                    external.attachments?.length ? `${external.attachments.length} attachment${external.attachments.length === 1 ? '' : 's'}` : null]
                    .filter(Boolean).map((part) => ` · ${part}`).join('')}
                </p>
              )}
              {draft.rationale && <p>{draft.rationale}</p>}
              {draft.category && <p className="draft-meta">Suggested category: <strong>{draft.category.replaceAll('_', ' ')}</strong></p>}
              {(draft.warnings || []).map((warning) => <p key={warning} className="draft-warning">{warning}</p>)}
            </div>
            <button className="button button-secondary button-small" type="button" onClick={() => setDraft(null)}><RotateCcw size={14} />Start over</button>
          </section>

          {(draft.missingInformation || []).length > 0 && (
            <section className="content-panel missing-panel">
              <div className="panel-heading"><div><span className="eyebrow">BEFORE YOU SAVE</span><h2>Information still missing</h2><p>Add these if you know them; they improve the investigation.</p></div><ListChecks size={17} /></div>
              <ul className="plain-list">{draft.missingInformation.map((item) => <li key={item}>{item}</li>)}</ul>
            </section>
          )}

          {draft.evidence && (
            <section className="content-panel evidence-preview">
              <label className="checkbox-row">
                <input type="checkbox" checked={attachEvidence} onChange={(event) => setAttachEvidence(event.target.checked)} />
                <span><strong>Attach the source material as evidence</strong><small>{draft.evidence.type.replaceAll('_', ' ')} · {draft.evidence.label}{draft.evidence.redactionCount ? ` · ${draft.evidence.redactionCount} secret(s) redacted` : ''}</small></span>
              </label>
              <pre className="evidence-content">{draft.evidence.content.slice(0, 4000)}{draft.evidence.content.length > 4000 ? '\n…' : ''}</pre>
            </section>
          )}

          <section className="content-panel form-panel">
            <DefectForm key={JSON.stringify(form.values).length + draft.title} initialValues={form.values} aiFields={form.aiFields} onSubmit={createDefect} loading={saving} submitLabel="Confirm and create defect" footerNote="Fields marked AI were suggested. Edit anything that is wrong, then confirm." />
          </section>
        </>
      )}
    </div>
  )
}
