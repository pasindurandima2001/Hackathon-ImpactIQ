import { useEffect, useState } from 'react'
import { projectApi } from '../services/api.js'

/** Project + component selectors. Every project is listed: any user may file a defect under any project. */
export default function ProjectScopeFields({ projectId, componentId, onChange, optional = true, disabled = false }) {
  const [projects, setProjects] = useState([])
  const [loadError, setLoadError] = useState('')

  useEffect(() => {
    let active = true
    projectApi.list()
      .then((rows) => { if (active) setProjects(rows) })
      .catch(() => { if (active) setLoadError('Projects could not be loaded.') })
    return () => { active = false }
  }, [])

  const selected = projects.find((project) => String(project.id) === String(projectId ?? ''))
  const components = selected?.components || []

  return (
    <>
      <label className="form-field">
        <span>Project{optional ? '' : ' *'}</span>
        <select value={projectId ?? ''} disabled={disabled} onChange={(event) => onChange({ projectId: event.target.value ? Number(event.target.value) : null, componentId: null })}>
          {optional && <option value="">No project (visible to you and admins)</option>}
          {!optional && <option value="" disabled>Select a project</option>}
          {projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}
        </select>
        {loadError && <small className="field-note error-note">{loadError}</small>}
      </label>
      <label className="form-field">
        <span>Component</span>
        <select value={componentId ?? ''} disabled={disabled || !selected} onChange={(event) => onChange({ projectId, componentId: event.target.value ? Number(event.target.value) : null })}>
          <option value="">{selected ? 'Whole project' : 'Select a project first'}</option>
          {components.map((component) => <option key={component.id} value={component.id}>{component.name}</option>)}
        </select>
      </label>
    </>
  )
}
