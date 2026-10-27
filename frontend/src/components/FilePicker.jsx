import { useRef, useState } from 'react'
import { FileUp, X } from 'lucide-react'
import { ALLOWED_UPLOAD_TYPES, MAX_UPLOAD_BYTES } from '../services/api.js'

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/**
 * Client-side pre-check only (type and size) for fast feedback; the server re-validates every upload,
 * including content checks, and is the source of truth.
 */
export default function FilePicker({ file, onChange, label = 'Choose a file', disabled = false }) {
  const inputRef = useRef(null)
  const [error, setError] = useState('')
  const [dragging, setDragging] = useState(false)

  function accept(candidate) {
    setError('')
    if (!candidate) return
    const extension = `.${candidate.name.split('.').pop()?.toLowerCase()}`
    if (!ALLOWED_UPLOAD_TYPES.split(',').includes(extension)) {
      setError(`Unsupported file type ${extension}.`)
      return
    }
    if (candidate.size > MAX_UPLOAD_BYTES) {
      setError(`File is ${formatSize(candidate.size)}; the limit is 10 MB.`)
      return
    }
    onChange(candidate)
  }

  return (
    <div className="file-picker-wrap">
      <div
        className={`file-picker${dragging ? ' dragging' : ''}${disabled ? ' disabled' : ''}`}
        onDragOver={(event) => { event.preventDefault(); setDragging(true) }}
        onDragLeave={() => setDragging(false)}
        onDrop={(event) => { event.preventDefault(); setDragging(false); if (!disabled) accept(event.dataTransfer.files?.[0]) }}
      >
        <FileUp size={20} />
        {file ? (
          <span className="file-picker-selected"><strong>{file.name}</strong><small>{formatSize(file.size)}</small></span>
        ) : (
          <span className="file-picker-copy"><strong>{label}</strong><small>TXT, LOG, MD, JSON, PDF or code files · max 10 MB</small></span>
        )}
        {file ? (
          <button type="button" className="icon-button" aria-label="Remove file" onClick={() => { onChange(null); if (inputRef.current) inputRef.current.value = '' }}><X size={15} /></button>
        ) : (
          <button type="button" className="button button-secondary button-small" disabled={disabled} onClick={() => inputRef.current?.click()}>Browse</button>
        )}
        <input ref={inputRef} type="file" hidden accept={ALLOWED_UPLOAD_TYPES} onChange={(event) => accept(event.target.files?.[0])} />
      </div>
      {error && <small className="field-note error-note">{error}</small>}
    </div>
  )
}
