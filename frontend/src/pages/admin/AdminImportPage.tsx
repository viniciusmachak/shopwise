import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api, type ImportReport } from '../../api/client'
import { AdminNav } from '../../components/AdminNav'
import { t } from '../../i18n'
import { formatDate } from '../orderStatus'

export function AdminImportPage() {
  const client = useQueryClient()
  const history = useQuery({ queryKey: ['admin', 'imports'], queryFn: api.admin.imports })
  const [file, setFile] = useState<File | null>(null)
  const upload = useMutation({
    mutationFn: (f: File) => api.admin.importCatalog(f),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['admin', 'imports'] })
      void client.invalidateQueries({ queryKey: ['admin', 'products'] })
    },
  })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (file) upload.mutate(file)
  }

  return (
    <section className="admin-page">
      <AdminNav />
      <h1>{t('import.title')}</h1>
      <p>{t('import.help')}</p>
      <p>
        <a href="/api/admin/imports/template" download>
          {t('import.template')}
        </a>
      </p>
      <form onSubmit={onSubmit} className="form inline-form">
        <label>
          {t('import.file')}
          <input type="file" accept=".csv,text/csv" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        </label>
        <button type="submit" disabled={!file || upload.isPending}>
          {upload.isPending ? t('import.running') : t('import.run')}
        </button>
      </form>
      {upload.isError && <p role="alert">{upload.error.message}</p>}
      {upload.data && <ImportResult report={upload.data} />}

      <h2>{t('import.history')}</h2>
      {history.data?.length === 0 && <p>{t('import.noHistory')}</p>}
      <ul className="order-list">
        {history.data?.map((r) => (
          <li key={r.id}>
            {formatDate(r.startedAt)} · {r.fileName} · {summary(r)}
          </li>
        ))}
      </ul>
    </section>
  )
}

function summary(r: ImportReport) {
  return t('import.summary', { read: r.read, imported: r.imported, skipped: r.skipped })
}

export function ImportResult({ report }: { report: ImportReport }) {
  return (
    <div role="status" className="import-result">
      <p>
        <strong>{report.status === 'COMPLETED' ? t('import.done') : t('import.failed')}</strong> {summary(report)}
      </p>
      {report.errors.length > 0 && (
        <>
          <p>{t('import.skippedRows')}</p>
          <ul>
            {report.errors.map((e, i) => (
              <li key={i}>
                {e.line > 0 ? t('import.line', { line: e.line }) : ''} {e.message}
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}
