import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { BookOpenText, ChevronDown, FolderKanban, History, Pencil, Plus, RefreshCw, Search, ShieldCheck, Sparkles, Trash2, Upload, UserPlus, Users, X } from 'lucide-react'
import ErrorState from '../components/ErrorState.jsx'
import FilePicker from '../components/FilePicker.jsx'
import LoadingState from '../components/LoadingState.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { formatDate, getApiErrorMessage, knowledgeApi, projectApi, userApi } from '../services/api.js'

const ROLES = ['VIEWER', 'EDITOR', 'OWNER']
const BUSY = ['PENDING', 'PROCESSING']
const POLL_MS = 2500
const DISCOVERY_REFRESH_MS = [4000, 12000, 30000]
const atLeast = (role, required) => ROLES.indexOf(role) >= ROLES.indexOf(required)

/** Success or error message shown inside the panel that triggered it, so it is visible where the user is looking. */
function PanelFeedback({ feedback, scope }) {
  if (feedback.scope !== scope) return null
  return feedback.type === 'error'
    ? <div className="form-error panel-feedback" role="alert">{feedback.message}</div>
    : <div className="success-feedback panel-feedback" role="status">{feedback.message}</div>
}

function formatSize(bytes) {
  if (bytes == null) return '—'
  return bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function NewProject({ onCreated }) {
  const [open, setOpen] = useState(false)
  const [values, setValues] = useState({ name: '', description: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(event) {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      const project = await projectApi.create({ name: values.name.trim(), description: values.description.trim() || null })
      setValues({ name: '', description: '' })
      setOpen(false)
      onCreated(project)
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Project could not be created.'))
    } finally {
      setBusy(false)
    }
  }

  if (!open) return <button className="button button-primary" onClick={() => setOpen(true)}><Plus size={16} />New project</button>
  return (
    <form className="inline-create" onSubmit={submit}>
      <input value={values.name} onChange={(event) => setValues({ ...values, name: event.target.value })} placeholder="Project name" maxLength={120} required autoFocus />
      <input value={values.description} onChange={(event) => setValues({ ...values, description: event.target.value })} placeholder="Description (optional)" maxLength={1000} />
      <button className="button button-primary" type="submit" disabled={busy}>{busy ? 'Creating…' : 'Create'}</button>
      <button className="icon-button" type="button" onClick={() => setOpen(false)} aria-label="Cancel"><X size={16} /></button>
      {error && <div className="form-error" role="alert">{error}</div>}
    </form>
  )
}

/** OWNER-only (admins act as OWNER) rename and delete. Delete asks for the project name to be typed. */
function ProjectSettings({ project, onUpdated, onDeleted }) {
  const [mode, setMode] = useState(null)
  const [values, setValues] = useState({ name: '', description: '' })
  const [confirmName, setConfirmName] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => { setMode(null); setError('') }, [project.id])

  function start(next) {
    setError('')
    setConfirmName('')
    setValues({ name: project.name, description: project.description || '' })
    setMode(next)
  }

  async function run(action, fallback) {
    setBusy(true)
    setError('')
    try {
      await action()
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, fallback))
    } finally {
      setBusy(false)
    }
  }

  function saveEdit(event) {
    event.preventDefault()
    run(async () => {
      const updated = await projectApi.update(project.id, { name: values.name.trim(), description: values.description.trim() || null })
      setMode(null)
      onUpdated(updated)
    }, 'Project could not be updated.')
  }

  function confirmDelete(event) {
    event.preventDefault()
    run(async () => {
      await projectApi.remove(project.id)
      onDeleted(project)
    }, 'Project could not be deleted.')
  }

  return (
    <section className="content-panel project-settings">
      {mode === 'edit' ? (
        <form className="inline-create" onSubmit={saveEdit}>
          <input value={values.name} onChange={(event) => setValues({ ...values, name: event.target.value })} placeholder="Project name" maxLength={120} required autoFocus aria-label="Project name" />
          <input value={values.description} onChange={(event) => setValues({ ...values, description: event.target.value })} placeholder="Description (optional)" maxLength={1000} aria-label="Project description" />
          <button className="button button-primary" type="submit" disabled={busy || !values.name.trim()}>{busy ? 'Saving…' : 'Save'}</button>
          <button className="button" type="button" onClick={() => setMode(null)} disabled={busy}>Cancel</button>
        </form>
      ) : mode === 'delete' ? (
        <form className="project-delete-confirm" onSubmit={confirmDelete}>
          <p><strong>Delete “{project.name}”?</strong> This permanently removes its {project.documentCount} document(s), indexed chunks, components, members and chat conversations. Projects that still have defects cannot be deleted.</p>
          <div className="inline-create">
            <input value={confirmName} onChange={(event) => setConfirmName(event.target.value)} placeholder="Type the project name to confirm" aria-label="Type the project name to confirm" autoFocus />
            <button className="button button-danger-outline" type="submit" disabled={busy || confirmName.trim() !== project.name}><Trash2 size={14} />{busy ? 'Deleting…' : 'Delete project'}</button>
            <button className="button" type="button" onClick={() => setMode(null)} disabled={busy}>Cancel</button>
          </div>
        </form>
      ) : (
        <div className="project-settings-row">
          <div><span className="eyebrow">PROJECT</span><h3>{project.name}</h3><p>{project.description || 'No description'}</p></div>
          <div className="project-settings-actions">
            <button className="button" type="button" onClick={() => start('edit')}><Pencil size={14} />Edit</button>
            <button className="button button-danger-outline" type="button" onClick={() => start('delete')}><Trash2 size={14} />Delete</button>
          </div>
        </div>
      )}
      {error && <div className="form-error" role="alert">{error}</div>}
    </section>
  )
}

function DocumentRow({ document, projectId, role, userCanDelete, onChanged }) {
  const [open, setOpen] = useState(false)
  const [detail, setDetail] = useState(null)
  const [versionFile, setVersionFile] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const latest = document.latestVersion
  const processing = BUSY.includes(latest?.status)

  const loadDetail = useCallback(async () => {
    try {
      setDetail(await knowledgeApi.get(projectId, document.id))
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Versions could not be loaded.'))
    }
  }, [projectId, document.id])

  useEffect(() => { if (open) loadDetail() }, [open, loadDetail, document.updatedAt, latest?.status])

  async function run(action, fallback) {
    setBusy(true)
    setError('')
    try {
      await action()
      onChanged()
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, fallback))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <tr className={open ? 'row-expanded' : undefined}>
        <td>
          <button type="button" className="doc-title-button" onClick={() => setOpen((value) => !value)} aria-expanded={open}>
            <ChevronDown size={14} className={`hypothesis-chevron${open ? ' open' : ''}`} />
            <span><strong>{document.title}</strong><small>{latest?.originalFilename} · {document.sourceType.toUpperCase()} · {formatSize(latest?.sizeBytes)}</small></span>
          </button>
        </td>
        <td>{document.componentName || <span className="cell-muted">Whole project</span>}</td>
        <td><StatusBadge status={processing ? latest.status : document.status} /></td>
        <td>{document.activeVersionNumber ? `v${document.activeVersionNumber}` : '—'}{document.latestVersionNumber !== document.activeVersionNumber && <small className="cell-muted">latest v{document.latestVersionNumber}</small>}</td>
        <td>{latest?.chunkCount ?? '—'}{latest?.pageCount ? <small className="cell-muted">{latest.pageCount} pages</small> : null}</td>
        <td>{latest?.embeddingStatus ? <span className={`risk-badge ${latest.embeddingStatus === 'EMBEDDED' ? 'risk-low' : 'risk-medium'}`}>{latest.embeddingStatus === 'EMBEDDED' ? 'Hybrid' : 'Lexical only'}</span> : '—'}</td>
        <td className="date-cell">{document.uploadedBy || '—'}<small className="cell-muted">{formatDate(document.updatedAt)}</small></td>
        <td>
          <div className="row-actions">
            {atLeast(role, 'EDITOR') && <button className="icon-button" title="Reprocess" aria-label="Reprocess" disabled={busy || processing} onClick={() => run(() => knowledgeApi.reprocess(projectId, document.id), 'Reprocess failed.')}><RefreshCw size={14} /></button>}
            {userCanDelete && <button className="icon-button" title="Delete document and its indexed data" aria-label="Delete" disabled={busy} onClick={() => window.confirm(`Delete "${document.title}" with all versions and indexed chunks?`) && run(() => knowledgeApi.remove(projectId, document.id), 'Delete failed.')}><Trash2 size={14} /></button>}
          </div>
        </td>
      </tr>
      {(document.lastError || error) && (
        <tr className="row-error"><td colSpan={8}>{error || document.lastError}</td></tr>
      )}
      {open && (
        <tr className="row-detail">
          <td colSpan={8}>
            <div className="version-panel">
              <div>
                <h4>Versions</h4>
                {detail ? (
                  <ul className="version-list">
                    {detail.versions.map((version) => (
                      <li key={version.id}>
                        <strong>v{version.versionNumber}{version.active ? ' · active' : ''}</strong>
                        <StatusBadge status={version.status} />
                        <span>{version.originalFilename} · {formatSize(version.sizeBytes)} · {version.chunkCount ?? 0} chunks{version.redactionCount ? ` · ${version.redactionCount} secrets redacted` : ''}{version.fileDeleted ? ' · original removed by retention' : ''}</span>
                        <small>{version.uploadedBy || '—'} · {formatDate(version.createdAt)} · sha256 {version.sha256.slice(0, 12)}…</small>
                        {version.errorMessage && <p className="agent-step-error">{version.errorMessage}</p>}
                      </li>
                    ))}
                  </ul>
                ) : <LoadingState label="Loading versions" />}
              </div>
              {atLeast(role, 'EDITOR') && (
                <div className="version-upload">
                  <h4>Upload a new version</h4>
                  <FilePicker file={versionFile} onChange={setVersionFile} disabled={busy || processing} />
                  <button className="button button-secondary button-small" disabled={!versionFile || busy || processing} onClick={() => run(async () => { await knowledgeApi.uploadVersion(projectId, document.id, versionFile); setVersionFile(null) }, 'Version upload failed.')}><Upload size={13} />Upload version</button>
                  <small className="muted-note">The previous version stays searchable until the new one is indexed.</small>
                </div>
              )}
            </div>
          </td>
        </tr>
      )}
    </>
  )
}

export default function ProjectKnowledgePage() {
  const { user } = useAuth()
  const [projects, setProjects] = useState([])
  const [projectId, setProjectId] = useState(null)
  const [project, setProject] = useState(null)
  const [documents, setDocuments] = useState([])
  const [members, setMembers] = useState([])
  const [audit, setAudit] = useState([])
  const [componentFilter, setComponentFilter] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [upload, setUpload] = useState({ file: null, componentId: '', title: '' })
  const [uploading, setUploading] = useState(false)
  const [componentName, setComponentName] = useState('')
  const [member, setMember] = useState({ username: '', role: 'VIEWER' })
  const [users, setUsers] = useState([])
  const [feedback, setFeedback] = useState({ scope: '', type: '', message: '' })
  const [detecting, setDetecting] = useState(false)

  const loadProjects = useCallback(async (selectId) => {
    try {
      const rows = await projectApi.list()
      setProjects(rows)
      setProjectId((current) => selectId ?? current ?? rows[0]?.id ?? null)
      setError('')
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Projects could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }, [])

  const loadProject = useCallback(async () => {
    if (!projectId) return
    try {
      const [detail, docs, people] = await Promise.all([
        projectApi.get(projectId), knowledgeApi.list(projectId), projectApi.members(projectId),
      ])
      setProject(detail)
      setDocuments(docs)
      setMembers(people)
      setUsers(await userApi.list().catch(() => []))
      setAudit(detail.myRole === 'OWNER' ? await projectApi.audit(projectId).catch(() => []) : [])
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Project could not be loaded.'))
    }
  }, [projectId])

  useEffect(() => { loadProjects() }, [loadProjects])
  useEffect(() => { loadProject() }, [loadProject])

  const anyProcessing = documents.some((document) => BUSY.includes(document.latestVersion?.status) || BUSY.includes(document.status))
  useEffect(() => {
    if (!anyProcessing) return undefined
    const timer = setInterval(loadProject, POLL_MS)
    return () => clearInterval(timer)
  }, [anyProcessing, loadProject])

  // AI component discovery runs in the background right after indexing, so refresh a few times once it finishes.
  const wasProcessing = useRef(false)
  useEffect(() => {
    if (anyProcessing) {
      wasProcessing.current = true
      return undefined
    }
    if (!wasProcessing.current) return undefined
    wasProcessing.current = false
    const timers = DISCOVERY_REFRESH_MS.map((delay) => setTimeout(loadProject, delay))
    return () => timers.forEach(clearTimeout)
  }, [anyProcessing, loadProject])

  async function detectComponents() {
    setDetecting(true)
    setError('')
    setNotice('')
    try {
      const result = await projectApi.discoverComponents(projectId)
      setNotice(result.added.length
        ? `${result.message} ${result.added.map((item) => item.name).join(', ')}`
        : result.message)
      await loadProject()
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'AI component detection failed.'))
    } finally {
      setDetecting(false)
    }
  }

  const role = project?.myRole || 'VIEWER'
  const visibleDocuments = useMemo(() => documents.filter((document) => !componentFilter || String(document.componentId) === componentFilter), [documents, componentFilter])
  const availableUsers = useMemo(() => {
    const memberIds = new Set(members.map((item) => item.userId))
    return users.filter((account) => !memberIds.has(account.id))
  }, [users, members])

  /** Runs an action; with a scope, the result is shown inside that panel instead of at the top of the page. */
  async function guarded(action, fallback, success, scope) {
    setError('')
    setNotice('')
    setFeedback({ scope: '', type: '', message: '' })
    try {
      await action()
      if (success) {
        if (scope) setFeedback({ scope, type: 'success', message: success })
        else setNotice(success)
      }
      await loadProject()
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, fallback)
      if (scope) setFeedback({ scope, type: 'error', message })
      else setError(message)
    }
  }

  async function submitUpload(event) {
    event.preventDefault()
    setUploading(true)
    await guarded(async () => {
      await knowledgeApi.upload(projectId, { file: upload.file, componentId: upload.componentId || null, title: upload.title.trim() || null })
      setUpload({ file: null, componentId: upload.componentId, title: '' })
    }, 'Upload failed.', 'Uploaded. The document is being validated, extracted, chunked and indexed.')
    setUploading(false)
  }

  if (loading) return <LoadingState label="Loading project knowledge" />

  return (
    <div className="page-stack">
      <section className="welcome-row">
        <div><span className="eyebrow">KNOWLEDGE / RAG SOURCES</span><h2>Project knowledge</h2><p>Runbooks, designs, logs and code that agents retrieve from — only within the defect's own project.</p></div>
        <NewProject onCreated={(created) => loadProjects(created.id)} />
      </section>

      {error && <ErrorState message={error} onRetry={() => { setError(''); loadProject() }} />}
      {notice && <div className="success-feedback" role="status">{notice}</div>}

      {projects.length === 0 ? (
        <section className="analysis-empty"><div className="analysis-empty-mark"><FolderKanban size={22} /></div><div><span className="eyebrow">GET STARTED</span><h3>Create your first project</h3><p>Projects scope knowledge, defects and access. You become the project owner.</p></div></section>
      ) : (
        <>
          <div className="project-chips" role="tablist" aria-label="Projects">
            {projects.map((item) => (
              <button key={item.id} type="button" role="tab" aria-selected={item.id === projectId} className={`project-chip${item.id === projectId ? ' active' : ''}`} onClick={() => setProjectId(item.id)}>
                <FolderKanban size={14} /><span><strong>{item.name}</strong><small>{item.indexedDocumentCount}/{item.documentCount} indexed · {item.myRole}</small></span>
              </button>
            ))}
          </div>

          {project && (
            <>
              {role === 'OWNER' && (
                <ProjectSettings
                  project={project}
                  onUpdated={(updated) => { setProject(updated); setNotice('Project updated.'); loadProjects() }}
                  onDeleted={(deleted) => {
                    setProject(null)
                    setDocuments([])
                    setMembers([])
                    setAudit([])
                    setProjectId(null)
                    setNotice(`Project “${deleted.name}” was deleted.`)
                    loadProjects()
                  }}
                />
              )}
              <section className="stats-grid knowledge-stats">
                <article className="stat-card"><div className="stat-card-top"><span className="stat-label">Documents</span><span className="stat-icon"><BookOpenText size={17} /></span></div><div className="stat-value">{project.documentCount}</div><div className="stat-foot">{project.indexedDocumentCount} indexed</div></article>
                <article className="stat-card stat-blue"><div className="stat-card-top"><span className="stat-label">Indexed chunks</span><span className="stat-icon"><Search size={17} /></span></div><div className="stat-value">{project.chunkCount}</div><div className="stat-foot">Line and page references kept</div></article>
                <article className="stat-card stat-amber"><div className="stat-card-top"><span className="stat-label">Components</span><span className="stat-icon"><FolderKanban size={17} /></span></div><div className="stat-value">{project.components.length}</div><div className="stat-foot">Scope retrieval per component</div></article>
                <article className="stat-card stat-coral"><div className="stat-card-top"><span className="stat-label">Your access</span><span className="stat-icon"><ShieldCheck size={17} /></span></div><div className="stat-value role-value">{role}</div><div className="stat-foot">{members.length} member{members.length === 1 ? '' : 's'}</div></article>
              </section>

              <section className="content-panel">
                <div className="panel-heading">
                  <div><span className="eyebrow">STRUCTURE</span><h2>Components</h2><p>AI adds the components each uploaded document describes. You can also add or remove them manually.</p></div>
                  {atLeast(role, 'EDITOR') && (
                    <button className="button button-secondary" type="button" disabled={detecting || !project.indexedDocumentCount}
                      title={project.indexedDocumentCount ? 'Read the indexed documents and add the components they describe' : 'Upload and index a document first'}
                      onClick={detectComponents}><Sparkles size={14} />{detecting ? 'Detecting…' : 'Detect with AI'}</button>
                  )}
                </div>
                <div className="component-row">
                  {project.components.map((component) => (
                    <span className={`component-chip${component.description?.includes('(Detected by AI') ? ' component-chip-ai' : ''}`} key={component.id} title={component.description || undefined}>
                      {component.description?.includes('(Detected by AI') && <Sparkles size={11} aria-label="Detected by AI" />}{component.name}
                      {role === 'OWNER' && <button type="button" aria-label={`Delete ${component.name}`} onClick={() => window.confirm(`Delete component "${component.name}"?`) && guarded(() => projectApi.deleteComponent(projectId, component.id), 'Component could not be deleted.')}><X size={11} /></button>}
                    </span>
                  ))}
                  {atLeast(role, 'EDITOR') && (
                    <form className="component-add" onSubmit={(event) => { event.preventDefault(); guarded(async () => { await projectApi.addComponent(projectId, { name: componentName.trim() }); setComponentName('') }, 'Component could not be added.') }}>
                      <input value={componentName} onChange={(event) => setComponentName(event.target.value)} placeholder="New component" maxLength={120} required />
                      <button className="button button-secondary button-small" type="submit"><Plus size={13} />Add</button>
                    </form>
                  )}
                  {!project.components.length && !atLeast(role, 'EDITOR') && <span className="muted-note">No components defined.</span>}
                </div>
              </section>

              {atLeast(role, 'EDITOR') && (
                <form className="content-panel upload-panel" onSubmit={submitUpload}>
                  <div className="panel-heading"><div><span className="eyebrow">AUTHORIZED UPLOAD</span><h2>Add a knowledge source</h2><p>Validated (type, size, content), secrets redacted, then chunked and embedded. Scanned PDFs need a text export (OCR is not supported).</p></div><Upload size={17} /></div>
                  <div className="upload-body">
                    <FilePicker file={upload.file} onChange={(file) => setUpload({ ...upload, file })} disabled={uploading} />
                    <div className="form-grid">
                      <label className="form-field"><span>Component</span><select value={upload.componentId} onChange={(event) => setUpload({ ...upload, componentId: event.target.value })}><option value="">Whole project</option>{project.components.map((component) => <option key={component.id} value={component.id}>{component.name}</option>)}</select></label>
                      <label className="form-field"><span>Title</span><input value={upload.title} onChange={(event) => setUpload({ ...upload, title: event.target.value })} maxLength={255} placeholder="Defaults to the file name" /></label>
                    </div>
                    <div className="form-actions"><span>Stored outside the web root under a random name; never served directly.</span><button className="button button-primary" type="submit" disabled={!upload.file || uploading}><Upload size={15} />{uploading ? 'Uploading…' : 'Upload and index'}</button></div>
                  </div>
                </form>
              )}

              <section className="content-panel register-panel">
                <div className="register-toolbar">
                  <div className="register-count"><strong>{visibleDocuments.length}</strong><span>documents{anyProcessing ? ' · processing…' : ''}</span></div>
                  <div className="filter-controls">
                    <label className="select-filter"><span>Component</span><select value={componentFilter} onChange={(event) => setComponentFilter(event.target.value)}><option value="">All</option>{project.components.map((component) => <option key={component.id} value={component.id}>{component.name}</option>)}</select></label>
                    <button className="button button-secondary button-small" onClick={loadProject}><RefreshCw size={13} />Refresh</button>
                  </div>
                </div>
                {visibleDocuments.length ? (
                  <div className="table-scroll"><table className="defect-table knowledge-table"><thead><tr><th>Document</th><th>Component</th><th>Status</th><th>Version</th><th>Chunks</th><th>Retrieval</th><th>Uploaded</th><th /></tr></thead><tbody>
                    {visibleDocuments.map((document) => (
                      <DocumentRow key={document.id} document={document} projectId={projectId} role={role}
                        userCanDelete={role === 'OWNER' || (role === 'EDITOR' && document.uploadedBy === user?.username)} onChanged={loadProject} />
                    ))}
                  </tbody></table></div>
                ) : <div className="table-empty"><BookOpenText size={16} />No documents yet.</div>}
              </section>

              <section className="content-panel">
                <div className="panel-heading"><div><span className="eyebrow">ACCESS PERMISSIONS</span><h2>Members</h2><p>Every user can already read and chat about this project. Make a user EDITOR here so they can also file defects and run investigations in it.</p></div><Users size={17} /></div>
                <ul className="member-list">
                  {members.map((item) => (
                    <li key={item.userId}>
                      <span className="profile-avatar">{item.username.slice(0, 2).toUpperCase()}</span>
                      <span><strong>{item.displayName || item.username}</strong><small>{item.username} · since {formatDate(item.addedAt, false)}</small></span>
                      {role === 'OWNER' ? (
                        <>
                          <select aria-label={`Role of ${item.username}`} value={item.role} onChange={(event) => guarded(() => projectApi.updateMember(projectId, item.userId, event.target.value), 'Role could not be changed.', `${item.username} is now ${event.target.value}.`, 'members')}>{ROLES.map((value) => <option key={value} value={value}>{value}</option>)}</select>
                          <button className="icon-button" aria-label={`Remove ${item.username}`} onClick={() => window.confirm(`Remove ${item.username} from this project?`) && guarded(() => projectApi.removeMember(projectId, item.userId), 'Member could not be removed.', `${item.username} was removed.`, 'members')}><Trash2 size={14} /></button>
                        </>
                      ) : <span className="role-pill">{item.role}</span>}
                    </li>
                  ))}
                  {!members.length && <li className="muted-note">No members yet. Add a user below.</li>}
                </ul>
                <PanelFeedback feedback={feedback} scope="members" />
                {role === 'OWNER' && (
                  <form className="member-add" onSubmit={(event) => { event.preventDefault(); guarded(async () => { await projectApi.addMember(projectId, { username: member.username, role: member.role }); setMember({ username: '', role: 'VIEWER' }) }, 'Member could not be added.', `${member.username} was added as ${member.role}.`, 'members') }}>
                    <select aria-label="User to add" value={member.username} onChange={(event) => setMember({ ...member, username: event.target.value })} required>
                      <option value="" disabled>{availableUsers.length ? `Select a user (${availableUsers.length} available)` : 'All registered users are members'}</option>
                      {availableUsers.map((account) => (
                        <option key={account.id} value={account.username}>
                          {account.displayName && account.displayName !== account.username ? `${account.displayName} (${account.username})` : account.username}{account.role === 'ADMIN' ? ' · admin' : ''}{account.enabled ? '' : ' · disabled'}
                        </option>
                      ))}
                    </select>
                    <select aria-label="Project role" value={member.role} onChange={(event) => setMember({ ...member, role: event.target.value })}>{ROLES.map((value) => <option key={value} value={value}>{value}</option>)}</select>
                    <button className="button button-secondary button-small" type="submit" disabled={!member.username}><UserPlus size={13} />Add</button>
                  </form>
                )}
              </section>

              {role === 'OWNER' && (
                <section className="content-panel">
                  <div className="panel-heading"><div><span className="eyebrow">AUDIT HISTORY</span><h2>Recent activity</h2><p>Uploads, deletions, access changes and investigations. Retained for 365 days.</p></div><History size={17} /></div>
                  {audit.length ? (
                    <div className="table-scroll"><table className="defect-table audit-table"><thead><tr><th>When</th><th>Who</th><th>Action</th><th>Detail</th></tr></thead><tbody>
                      {audit.map((event) => <tr key={event.id}><td className="date-cell">{formatDate(event.createdAt)}</td><td>{event.actorUsername || 'system'}</td><td><span className="environment-pill">{event.action.replaceAll('_', ' ')}</span></td><td>{event.detail || '—'}</td></tr>)}
                    </tbody></table></div>
                  ) : <p className="history-empty">No activity recorded yet.</p>}
                </section>
              )}
            </>
          )}
        </>
      )}
    </div>
  )
}
