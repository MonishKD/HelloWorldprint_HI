import { useEffect, useRef, useState } from 'react';
import { clearLogs, useLogs } from './dashboardApi';

function readOpen() {
  try {
    return localStorage.getItem('fg-console') !== 'closed';
  } catch {
    return true;
  }
}

/** Shows every action sent and every error shown during this session, newest at the bottom. */
export default function OutputConsolePanel() {
  const logs = useLogs();
  const listRef = useRef<HTMLOListElement>(null);
  const [open, setOpen] = useState(readOpen);
  const errors = logs.filter((l) => l.kind === 'error').length;

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: 'smooth' });
  }, [logs, open]);

  function toggle() {
    setOpen(!open);
    try {
      localStorage.setItem('fg-console', open ? 'closed' : 'open');
    } catch {
      // not critical
    }
  }

  return (
    <aside className={`console ${open ? '' : 'is-closed'}`} aria-label="Output console">
      <div className="console-head">
        <button type="button" className="console-title" onClick={toggle} aria-expanded={open}>
          <span className="console-dot" aria-hidden="true" />
          Output console
          <span className="console-count">
            {logs.length}
            {errors > 0 && <span className="console-errors"> · {errors} err</span>}
          </span>
          <span className="console-chevron" aria-hidden="true">
            ▾
          </span>
        </button>
        {open && (
          <button className="console-clear" onClick={clearLogs} disabled={logs.length === 0}>
            Clear
          </button>
        )}
      </div>
      {open && (
        <ol ref={listRef} aria-live="polite">
          {logs.length === 0 && <li className="empty-log">No activity yet. Actions and errors will appear here.</li>}
          {logs.map((log) => (
            <li key={log.id} className={`log-${log.kind}`}>
              <time dateTime={log.at.toISOString()}>{log.at.toLocaleTimeString()}</time>
              <span className="tag">{log.kind === 'error' ? 'ERR' : log.kind === 'ok' ? 'OK' : 'SENT'}</span>
              <span>{log.message}</span>
            </li>
          ))}
        </ol>
      )}
    </aside>
  );
}
