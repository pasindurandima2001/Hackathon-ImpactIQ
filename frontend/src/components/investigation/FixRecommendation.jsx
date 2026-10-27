import { FlaskConical, ShieldAlert, Wrench } from 'lucide-react'

function TestList({ title, tests, empty }) {
  return (
    <div className="test-list">
      <h4>{title}</h4>
      {tests?.length ? (
        <ul>
          {tests.map((test, index) => (
            <li key={`${test.name}-${index}`}>
              <span className="test-type">{test.type.replaceAll('_', ' ')}</span>
              <div><strong>{test.name}</strong>{test.steps && <p>{test.steps}</p>}{test.expectedResult && <small>Expected: {test.expectedResult}</small>}</div>
            </li>
          ))}
        </ul>
      ) : <p className="muted-note">{empty}</p>}
    </div>
  )
}

export default function FixRecommendation({ fix }) {
  if (!fix) {
    return <p className="muted-note">No fix recommendation is available for this run. Retry the investigation once root-cause analysis succeeds.</p>
  }
  return (
    <div className="fix-grid">
      <section className="report-block">
        <div className="report-block-head"><Wrench size={15} /><h3>Recommended fix</h3></div>
        <ol className="fix-steps">
          {fix.steps.map((step, index) => (
            <li key={`${step.action}-${index}`}>
              <span className="action-number">{String(index + 1).padStart(2, '0')}</span>
              <div>
                <strong>{step.action}</strong>
                {step.rationale && <p>{step.rationale}</p>}
                <small>{step.hypothesisRank ? `Addresses hypothesis #${step.hypothesisRank}` : 'General hardening'}{step.risk ? ` · Risk: ${step.risk}` : ''}</small>
              </div>
            </li>
          ))}
        </ol>
        {fix.risks?.length > 0 && (
          <div className="risk-callout"><ShieldAlert size={14} /><div><strong>Risks and side effects</strong><ul>{fix.risks.map((risk) => <li key={risk}>{risk}</li>)}</ul></div></div>
        )}
      </section>
      <section className="report-block">
        <div className="report-block-head"><FlaskConical size={15} /><h3>Verification and regression tests</h3></div>
        <TestList title="Verify the fix" tests={fix.verificationTests} empty="No verification tests suggested." />
        <TestList title="Protect against regressions" tests={fix.regressionTests} empty="No regression tests suggested." />
      </section>
    </div>
  )
}
