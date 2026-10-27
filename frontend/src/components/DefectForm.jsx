import { useState } from 'react'
import { Save, Sparkles } from 'lucide-react'
import ProjectScopeFields from './ProjectScopeFields.jsx'

// Matches the backend DefectRequest.DESCRIPTION_MAX_LENGTH limit.
const DESCRIPTION_MAX_LENGTH = 20000
const levels = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const enums = {
  environment: ['DEVELOPMENT', 'TEST', 'STAGING', 'PRODUCTION'],
  businessCriticality: levels,
  customerImpact: levels,
  technicalImpact: levels,
  status: ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'],
}

const blankDefect = {
  title: '', description: '', environment: 'PRODUCTION', service: '', businessFunction: '',
  affectedUsers: 0, businessCriticality: 'MEDIUM',
  customerImpact: 'MEDIUM', technicalImpact: 'MEDIUM', status: 'OPEN',
  projectId: null, componentId: null,
}

function AiMark({ field, aiFields }) {
  if (!aiFields?.has(field)) return null
  return <span className="ai-mark" title="Suggested by AI — review before saving"><Sparkles size={10} />AI</span>
}

function SelectField({ name, label, value, options, onChange, aiFields }) {
  return (
    <label className={`form-field${aiFields?.has(name) ? ' ai-suggested' : ''}`}>
      <span>{label} <AiMark field={name} aiFields={aiFields} /></span>
      <select name={name} value={value || ''} onChange={onChange}>
        {options.map((option) => <option key={option} value={option}>{option.replaceAll('_', ' ')}</option>)}
      </select>
    </label>
  )
}

/**
 * @param aiFields optional Set of field names pre-filled from an AI draft; they are highlighted until edited.
 */
export default function DefectForm({ initialValues, onSubmit, loading = false, submitLabel = 'Save defect', aiFields, showStatus, footerNote }) {
  const [values, setValues] = useState(() => ({ ...blankDefect, ...initialValues }))
  const [suggested, setSuggested] = useState(() => new Set(aiFields || []))
  const [formError, setFormError] = useState('')
  const statusVisible = showStatus ?? Boolean(initialValues?.id)

  function handleChange(event) {
    const { name, value } = event.target
    setValues((current) => ({ ...current, [name]: value }))
    setSuggested((current) => {
      if (!current.has(name)) return current
      const next = new Set(current)
      next.delete(name)
      return next
    })
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError('')
    if (!values.title.trim() || !values.description.trim()) {
      setFormError('Title and description are required.')
      return
    }
    const payload = {
      title: values.title.trim(),
      description: values.description.trim(),
      environment: values.environment || null,
      service: values.service || null,
      businessFunction: values.businessFunction || null,
      affectedUsers: Number(values.affectedUsers),
      businessCriticality: values.businessCriticality || null,
      customerImpact: values.customerImpact || null,
      technicalImpact: values.technicalImpact || null,
      status: values.status || null,
      projectId: values.projectId || null,
      componentId: values.componentId || null,
    }
    if (!Number.isInteger(payload.affectedUsers) || payload.affectedUsers < 0) {
      setFormError('Affected users must be a non-negative whole number.')
      return
    }
    try {
      await onSubmit(payload)
    } catch (error) {
      setFormError(error?.message || 'Unable to save this defect.')
    }
  }

  return (
    <form className="defect-form" onSubmit={handleSubmit}>
      <section className="form-section">
        <div className="form-section-heading"><span className="section-index">01</span><div><h2>Incident details</h2><p>Describe the software behavior and where it occurs.</p></div></div>
        <label className={`form-field full-field${suggested.has('title') ? ' ai-suggested' : ''}`}>
          <span>Defect title <b>*</b> <AiMark field="title" aiFields={suggested} /></span>
          <input name="title" value={values.title} onChange={handleChange} placeholder="e.g. Checkout requests fail after release" maxLength={255} required />
        </label>
        <label className={`form-field full-field${suggested.has('description') ? ' ai-suggested' : ''}`}>
          <span>Description <b>*</b> <AiMark field="description" aiFields={suggested} /></span>
          <textarea name="description" value={values.description} onChange={handleChange} placeholder="What is failing? Include observed behavior and when it started." rows={6} maxLength={DESCRIPTION_MAX_LENGTH} required />
        </label>
        <div className="form-grid">
          <ProjectScopeFields projectId={values.projectId} componentId={values.componentId} onChange={(scope) => setValues((current) => ({ ...current, ...scope }))} />
          <SelectField name="environment" label="Environment" value={values.environment} options={enums.environment} onChange={handleChange} aiFields={suggested} />
          <label className={`form-field${suggested.has('service') ? ' ai-suggested' : ''}`}><span>Service <AiMark field="service" aiFields={suggested} /></span><input name="service" value={values.service || ''} onChange={handleChange} placeholder="e.g. checkout-api" maxLength={255} /></label>
          <label className={`form-field${suggested.has('businessFunction') ? ' ai-suggested' : ''}`}><span>Business function <AiMark field="businessFunction" aiFields={suggested} /></span><input name="businessFunction" value={values.businessFunction || ''} onChange={handleChange} placeholder="e.g. Payments" maxLength={255} /></label>
          <label className={`form-field${suggested.has('affectedUsers') ? ' ai-suggested' : ''}`}><span>Affected users <AiMark field="affectedUsers" aiFields={suggested} /></span><input name="affectedUsers" type="number" min="0" step="1" value={values.affectedUsers ?? 0} onChange={handleChange} /></label>
        </div>
      </section>

      <section className="form-section impact-input-section">
        <div className="form-section-heading"><span className="section-index">02</span><div><h2>Impact signals</h2><p>These drive the deterministic impact score. AI never sets the score itself.</p></div></div>
        <div className={`form-grid ${statusVisible ? 'form-grid-four' : 'form-grid-three'}`}>
          <SelectField name="businessCriticality" label="Business criticality" value={values.businessCriticality} options={enums.businessCriticality} onChange={handleChange} aiFields={suggested} />
          <SelectField name="customerImpact" label="Customer impact" value={values.customerImpact} options={enums.customerImpact} onChange={handleChange} aiFields={suggested} />
          <SelectField name="technicalImpact" label="Technical impact" value={values.technicalImpact} options={enums.technicalImpact} onChange={handleChange} aiFields={suggested} />
          {statusVisible && <SelectField name="status" label="Status" value={values.status} options={enums.status} onChange={handleChange} />}
        </div>
      </section>

      {formError && <div className="form-error" role="alert">{formError}</div>}
      <div className="form-actions">
        <span>{footerNote || <>Fields marked <b>*</b> are required</>}</span>
        <button className="button button-primary" type="submit" disabled={loading}>
          <Save size={16} />{loading ? 'Saving…' : submitLabel}
        </button>
      </div>
    </form>
  )
}
