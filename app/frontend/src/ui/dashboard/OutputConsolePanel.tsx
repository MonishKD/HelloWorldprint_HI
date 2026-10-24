import { useCallback, useEffect, useRef, useState } from 'react';
import { clearLogs, CONSOLE_PAGE, errorMessage, getConsoleFeed, logError, onCommandFinished, useLogs, type LogEntry } from './dashboardApi';

function readOpen() {
  try {
    return localStorage.getItem('fg-console') !== 'closed';
  } catch {
    return true;
  }
}

/**
 * The output console (R-DASH-06, #137): every command the signed-in user attempted and its outcome, from the backend's
 * command log (#140), oldest first, plus what this browser sent and the errors that never reached a command.
 */
export default function OutputConsolePanel() {
  const local = useLogs();
  const [saved, setSaved] = useState<LogEntry[]>([]);
  const [hasEarlier, setHasEarlier] = useState(false);
  const [clearedAt, setClearedAt] = useState(0);
  const listRef = useRef<HTMLOListElement>(null);
  const [open, setOpen] = useState(readOpen);

  // Newest page on open, and again after each command so its outcome shows up.
  const load = useCallback(() => {
    getConsoleFeed().then(
      (entries) => {
        // Keep pages loaded with "Load earlier" that are older than the newest page.
        setSaved((previous) => [...previous.filter((e) => entries.length > 0 && e.id < entries[0].id), ...entries]);
        setHasEarlier((had) => had || entries.length === CONSOLE_PAGE);
      },
      (e) => logError(`Load console: ${errorMessage(e)}`),
    );
  }, []);
  useEffect(() => {
    load();
    return onCommandFinished(load);
  }, [load]);

  function loadEarlier() {
    getConsoleFeed(saved[0]?.id).then(
      (entries) => {
        setSaved((previous) => [...entries, ...previous]);
        setHasEarlier(entries.length === CONSOLE_PAGE);
      },
      (e) => logError(`Load console: ${errorMessage(e)}`),
    );
  }

  // Both lists can share ids, so each line's key says where it came from.
  const lines = [...saved.map((e) => ({ e, key: `s${e.id}` })), ...local.map((e) => ({ e, key: `l${e.id}` }))]
    .filter(({ e }) => e.at.getTime() > clearedAt)
    .sort((a, b) => a.e.at.getTime() - b.e.at.getTime());
  const errors = lines.filter(({ e }) => e.kind === 'error').length;

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: 'smooth' });
  }, [lines.length, open]);

  function toggle() {
    setOpen(!open);
    try {
      localStorage.setItem('fg-console', open ? 'closed' : 'open');
    } catch {
      // not critical
    }
  }

  // Clearing hides what is there now; the backend keeps its log, so a reload shows it again.
  function clear() {
    setClearedAt(Date.now());
    setHasEarlier(false);
    clearLogs();
  }

  return (
    <aside className={`console ${open ? '' : 'is-closed'}`} aria-label="Output console">
      <div className="console-head">
        <button type="button" className="console-title" onClick={toggle} aria-expanded={open}>
          <span className="console-dot" aria-hidden="true" />
          Output console
          <span className="console-count">
            {lines.length}
            {errors > 0 && <span className="console-errors"> · {errors} err</span>}
          </span>
          <span className="console-chevron" aria-hidden="true">
            ▾
          </span>
        </button>
        {open && (
          <button className="console-clear" onClick={clear} disabled={lines.length === 0}>
            Clear
          </button>
        )}
      </div>
      {open && (
        <ol ref={listRef} aria-live="polite">
          {hasEarlier && (
            <li className="empty-log">
              <button type="button" className="console-clear" onClick={loadEarlier}>
                Load earlier
              </button>
            </li>
          )}
          {lines.length === 0 && <li className="empty-log">No activity yet. Actions and errors will appear here.</li>}
          {lines.map(({ e, key }) => (
            <li key={key} className={`log-${e.kind}`}>
              <time dateTime={e.at.toISOString()}>{e.at.toLocaleTimeString()}</time>
              <span className="tag">{e.kind === 'error' ? 'ERR' : e.kind === 'ok' ? 'OK' : 'SENT'}</span>
              <span>{e.message}</span>
            </li>
          ))}
        </ol>
      )}
    </aside>
  );
}
