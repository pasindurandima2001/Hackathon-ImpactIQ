import { useCallback, useEffect, useRef, useState } from 'react'
import { getApiErrorMessage, investigationApi } from '../services/api.js'

const ACTIVE = ['QUEUED', 'RUNNING']
const POLL_INTERVAL_MS = 1000

/** Investigation runs for one defect, with polling while the selected run is queued or running. */
export default function useInvestigations(defectId) {
  const [runs, setRuns] = useState([])
  const [selected, setSelected] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const timer = useRef(null)

  const loadRun = useCallback(async (investigationId) => {
    const run = await investigationApi.get(defectId, investigationId)
    setSelected(run)
    return run
  }, [defectId])

  const refreshList = useCallback(async () => {
    const list = await investigationApi.list(defectId)
    setRuns(list)
    return list
  }, [defectId])

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const list = await refreshList()
      if (list.length) await loadRun(list[0].id)
      else setSelected(null)
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Investigations could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }, [refreshList, loadRun])

  useEffect(() => { load() }, [load])

  const selectedId = selected?.summary?.id
  const selectedStatus = selected?.summary?.status
  useEffect(() => {
    clearTimeout(timer.current)
    if (!selectedId || !ACTIVE.includes(selectedStatus)) return undefined
    timer.current = setTimeout(async () => {
      try {
        const run = await loadRun(selectedId)
        if (!ACTIVE.includes(run.summary.status)) await refreshList()
      } catch {
        // Transient polling failure: the next tick or a manual refresh recovers.
      }
    }, POLL_INTERVAL_MS)
    return () => clearTimeout(timer.current)
  }, [selectedId, selectedStatus, selected, loadRun, refreshList])

  const act = useCallback(async (action, fallback) => {
    setBusy(true)
    setError('')
    try {
      const run = await action()
      setSelected(run)
      await refreshList()
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, fallback))
    } finally {
      setBusy(false)
    }
  }, [refreshList])

  const start = useCallback(() => act(() => investigationApi.start(defectId), 'The investigation could not be started.'), [act, defectId])
  const retry = useCallback((investigationId) => act(() => investigationApi.retry(defectId, investigationId), 'The investigation could not be retried.'), [act, defectId])
  const select = useCallback(async (investigationId) => {
    setError('')
    try {
      await loadRun(investigationId)
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'The investigation could not be loaded.'))
    }
  }, [loadRun])

  const giveFeedback = useCallback(async (payload) => {
    await investigationApi.feedback(defectId, selectedId, payload)
    await loadRun(selectedId)
    await refreshList()
  }, [defectId, selectedId, loadRun, refreshList])

  return {
    runs, selected, loading, busy, error, active: ACTIVE.includes(selectedStatus),
    reload: load, start, retry, select, giveFeedback,
  }
}
