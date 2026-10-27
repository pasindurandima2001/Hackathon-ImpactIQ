import { useEffect, useState } from 'react'
import { Boxes, Bug, FlaskConical, FolderKanban, Settings2 } from 'lucide-react'
import { Link } from 'react-router-dom'

export const defectKey = (id) => `IQ-${String(id).padStart(4, '0')}`

/** Project / component / defect pickers. Choices are only suggestions: the server validates every ID. */
export function ContextFields({ projects, defects, value, onChange, lockProject }) {
  const project = projects.find((item) => item.id === Number(value.projectId))
  const projectDefects = defects.filter((defect) => defect.projectId === Number(value.projectId))
  return (
    <div className="chat-context-fields">
      <label className="form-field">
        <span>Project <b>*</b></span>
        <select value={value.projectId || ''} disabled={lockProject}
          onChange={(event) => onChange({ projectId: event.target.value, componentId: '', defectId: '' })}>
          <option value="">Select a project</option>
          {projects.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
        </select>
      </label>
      <label className="form-field">
        <span>Component (optional)</span>
        <select value={value.componentId || ''} disabled={!project} onChange={(event) => onChange({ ...value, componentId: event.target.value })}>
          <option value="">Whole project</option>
          {(project?.components || []).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
        </select>
      </label>
      <label className="form-field">
        <span>Defect (optional)</span>
        <select value={value.defectId || ''} disabled={!project} onChange={(event) => onChange({ ...value, defectId: event.target.value })}>
          <option value="">No defect</option>
          {projectDefects.map((defect) => <option key={defect.id} value={defect.id}>{defectKey(defect.id)} · {defect.title}</option>)}
        </select>
      </label>
    </div>
  )
}

/** Shows the active conversation's context and the server-computed Start Investigation action. */
export default function ChatContextBar({ detail, projects, defects, onChangeContext, onStartInvestigation }) {
  const conversation = detail.conversation
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState({})
  useEffect(() => {
    setEditing(false)
    setDraft({ projectId: String(conversation.projectId), componentId: conversation.componentId ? String(conversation.componentId) : '', defectId: conversation.defectId ? String(conversation.defectId) : '' })
  }, [conversation.id, conversation.projectId, conversation.componentId, conversation.defectId])
  const action = detail.actions?.find((item) => item.type === 'START_INVESTIGATION')

  async function apply() {
    const ok = await onChangeContext({ componentId: draft.componentId ? Number(draft.componentId) : null, defectId: draft.defectId ? Number(draft.defectId) : null })
    if (ok !== false) setEditing(false)
  }

  return (
    <section className="chat-context" aria-label="Conversation context">
      <div className="chat-context-chips">
        <span className="chat-context-chip"><FolderKanban size={13} />{conversation.projectName || `Project ${conversation.projectId}`}</span>
        {conversation.componentName && <span className="chat-context-chip"><Boxes size={13} />{conversation.componentName}</span>}
        {conversation.defectId && (
          <Link className="chat-context-chip chat-context-defect" to={`/defects/${conversation.defectId}`} title={conversation.defectTitle || ''}>
            <Bug size={13} />{defectKey(conversation.defectId)}{conversation.defectTitle ? ` · ${conversation.defectTitle}` : ''}
          </Link>
        )}
        {detail.context?.projectRole && <span className="chat-context-role">Your role: {detail.context.projectRole}</span>}
        <button type="button" className="text-link chat-context-edit" onClick={() => setEditing((value) => !value)} aria-expanded={editing}>
          <Settings2 size={13} />{editing ? 'Close' : 'Change context'}
        </button>
        {action && (
          <span className="chat-action">
            <button type="button" className="button button-secondary button-small" disabled={!action.enabled} onClick={() => onStartInvestigation(action)} title={action.reason}>
              <FlaskConical size={13} />{action.label}
            </button>
            {!action.enabled && <small>{action.reason}</small>}
          </span>
        )}
      </div>
      {editing && (
        <div className="chat-context-editor">
          <ContextFields projects={projects} defects={defects} value={draft} onChange={setDraft} lockProject />
          <button type="button" className="button button-primary button-small" onClick={apply}>Apply</button>
        </div>
      )}
    </section>
  )
}
