import { useState, type CSSProperties, type FormEvent } from 'react';
import { Link, Navigate, Route, Routes, useNavigate, useParams } from 'react-router-dom';
import type { Party, PartyCheckout, PartyLine, PartyMember } from '../../domain';
import { useUser } from '../../session';
import { ConfirmButton, Empty, LoadError, Loading, money, Monogram, stagger, useLoad, usePolling } from '../common';
import { errorMessage, send } from '../dashboard/dashboardApi';
import { Icon } from '../icons';
import CardPayment, { type ConfirmCard } from '../prc/CardPayment';
import FareLines from '../prc/FareLines';
import CardRoulette, { hueOf } from './CardRoulette';
import {
  acceptRoulette,
  cancelParty,
  confirmShare,
  declineRoulette,
  getMyParty,
  getParty,
  joinParty,
  lockSplit,
  normalizeCode,
  proposeRoulette,
  startShareCheckout,
  toggleClaim,
} from './partyApi';

/** "K7Q2MX" -> "K7Q-2MX", easier to read out loud. */
const showCode = (code: string) => code.replace(/^(.{3})(.+)$/, '$1-$2');

/** Party mode (/customer/party): join with a code, or the party room itself. */
export default function PartyPage() {
  return (
    <div className="party">
      <Routes>
        <Route index element={<PartyLobby />} />
        <Route path=":code" element={<PartyRoom />} />
        <Route path="*" element={<Navigate to="/customer/party" replace />} />
      </Routes>
    </div>
  );
}

// ---------- Lobby ----------

function PartyLobby() {
  const navigate = useNavigate();
  const { data: mine } = useLoad(getMyParty, []);
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function join(e: FormEvent) {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      const party = await send(`Join party ${showCode(normalizeCode(code))}`, () => joinParty(code));
      navigate(`/customer/party/${party.code}`);
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  }

  return (
    <>
      <section className="party-hero rise">
        <PartyLights />
        <div className="party-hero-main">
          <span className="eyebrow">Party mode</span>
          <h1>Order together.</h1>
          <p>One order, one delivery, everyone at the table. Split it by what each person eats, or let the card roulette pick who pays for all of it.</p>
        </div>
      </section>

      {mine && (
        <section className="card party-resume rise" style={stagger(1)}>
          <div>
            <strong>You're in a party at {mine.restaurantName}</strong>
            <small className="muted">
              Code {showCode(mine.code)} · {mine.members.length} {mine.members.length === 1 ? 'person' : 'people'}
            </small>
          </div>
          <Link className="btn btn-primary btn-sm" to={`/customer/party/${mine.code}`}>
            Back to the party
          </Link>
        </section>
      )}

      <div className="party-lobby-grid">
        <form className="card party-join rise" style={stagger(2)} onSubmit={join}>
          <h2>Got a code?</h2>
          <label className="field">
            <span>Party code</span>
            <input
              className="party-code-input"
              value={code}
              onChange={(e) => setCode(e.target.value.toUpperCase())}
              placeholder="ABC-123"
              maxLength={7}
              autoComplete="off"
              spellCheck={false}
              aria-describedby="party-code-hint"
            />
            <small id="party-code-hint" className="muted">
              The host sees it at the top of their party.
            </small>
          </label>
          {error && (
            <p className="alert alert-error" role="alert">
              {error}
            </p>
          )}
          <button className="btn btn-primary btn-block" disabled={busy || normalizeCode(code).length < 6}>
            <Icon name="users" size={16} /> {busy ? 'Joining…' : 'Join the party'}
          </button>
        </form>

        <section className="card party-how rise" style={stagger(3)}>
          <h2>Hosting?</h2>
          <ol>
            <li>
              <strong>Fill a cart</strong> at any open restaurant and tap <em>Make it a party</em>.
            </li>
            <li>
              <strong>Share the code.</strong> Friends join and tap what they're eating; a shared item is split evenly.
            </li>
            <li>
              <strong>Split it, or spin.</strong> Lock the split, or start the card roulette: if everyone accepts, one card pays for all of it.
            </li>
          </ol>
          <Link className="btn btn-ghost btn-sm" to="/customer">
            Browse restaurants
          </Link>
        </section>
      </div>
    </>
  );
}

// ---------- Room ----------

/** Roulette results already animated for a user (party:user), so polling or coming back doesn't spin them again. */
const spun = new Set<string>();

function PartyRoom() {
  const { code = '' } = useParams();
  const user = useUser();
  const { data: party, error, reload } = useLoad(() => getParty(code), [code]);
  usePolling(reload, 2000);
  const [actionError, setActionError] = useState('');

  /** Every party action: logged to the console like the rest of the app, then the room re-reads the party. */
  async function act(label: string, call: () => Promise<unknown>) {
    setActionError('');
    try {
      await send(label, call);
    } catch (e) {
      setActionError(errorMessage(e));
    }
    reload();
  }

  if (error && !party) return <LoadError what="the party" message={error} onRetry={reload} />;
  if (!party) return <Loading what="the party" />;

  const me = party.members.find((m) => m.id === user.id);
  const host = party.members.find((m) => m.host)!;
  const label = (what: string) => `${what} (party ${showCode(party.code)})`;

  return (
    <>
      <PartyHeader party={party} host={host} />
      {actionError && (
        <p className="alert alert-error" role="alert">
          {actionError}
        </p>
      )}

      {party.status === 'cancelled' ? (
        <Empty icon="users" title="This party was cancelled">
          <Link to="/customer/party">Back to party mode</Link>
        </Empty>
      ) : !me ? (
        <section className="card party-invite rise">
          <h2>{host.name} invited you</h2>
          {party.status === 'gathering' ? (
            <>
              <p className="muted">Join to pick what you're eating at {party.restaurantName}.</p>
              <button className="btn btn-primary" onClick={() => act(label('Join'), () => joinParty(party.code))}>
                <Icon name="users" size={16} /> Join {host.name}'s party
              </button>
            </>
          ) : (
            <p className="muted">This party already locked its order.</p>
          )}
        </section>
      ) : party.status === 'placed' ? (
        <Celebration party={party} host={host} isHost={me.host} meId={me.id} />
      ) : (
        <>
          {party.status === 'roulette' && <RouletteVote party={party} me={me} act={act} label={label} />}
          {party.roulettePayerId && (
            <section className="card party-roulette rise">
              <CardRoulette
                members={party.members}
                payerId={party.roulettePayerId}
                spin={!spun.has(`${party.code}:${me.id}`)}
                onDone={() => spun.add(`${party.code}:${me.id}`)}
              />
            </section>
          )}
          <div className="party-grid">
            <PartyItems party={party} me={me} host={host} act={act} label={label} />
            <SharePanel party={party} me={me} host={host} act={act} label={label} onPaid={reload} />
          </div>
        </>
      )}
    </>
  );
}

type Act = (label: string, call: () => Promise<unknown>) => void;
type Label = (what: string) => string;

function PartyHeader({ party, host }: { party: Party; host: PartyMember }) {
  const [copied, setCopied] = useState(false);
  const step = party.status === 'placed' ? 2 : party.status === 'paying' ? 1 : 0;

  async function copy() {
    try {
      await navigator.clipboard.writeText(showCode(party.code));
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    } catch {
      setCopied(false); // no clipboard access: the code is on screen anyway
    }
  }

  return (
    <section className="party-hero rise">
      <PartyLights />
      <div className="party-hero-main">
        <span className="eyebrow">Party at {party.restaurantName}</span>
        <h1>{host.name}'s party</h1>
        <ol className="party-steps" aria-label="Party progress">
          {['Pick your food', 'Pay', 'Order placed'].map((s, i) => (
            <li key={s} className={i < step ? 'is-done' : i === step ? 'is-now' : ''} aria-current={i === step ? 'step' : undefined}>
              {s}
            </li>
          ))}
        </ol>
      </div>
      <div className="party-code">
        <span>Party code</span>
        <span className="party-code-tiles" aria-label={`Party code ${party.code.split('').join(' ')}`}>
          {showCode(party.code)
            .split('')
            .map((c, i) =>
              c === '-' ? (
                <i key={i} className="party-code-dash" aria-hidden="true" />
              ) : (
                <b key={i} style={stagger(i)} aria-hidden="true">
                  {c}
                </b>
              ),
            )}
        </span>
        <button type="button" className="btn btn-sm party-copy" onClick={copy}>
          <Icon name={copied ? 'check' : 'copy'} size={14} /> {copied ? 'Copied' : 'Copy code'}
        </button>
      </div>
      <ul className="party-crowd" aria-label={`${party.members.length} in the party`}>
        {party.members.map((m, i) => (
          <li key={m.id} className="party-guest" style={{ ...stagger(i), '--r': `${(i % 2 ? 1 : -1) * 4}deg` } as CSSProperties} title={m.name}>
            <Monogram name={m.name} hue={hueOf(m.id)} />
            <span>{m.name.split(' ')[0]}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** A member's part of an item as people say it at the table: 1.5 -> "1 1/2", 0.333… -> "1/3". */
export function portion(part: number): string {
  const whole = Math.floor(part + 1e-9);
  const frac = part - whole;
  if (frac < 1e-9) return String(whole);
  for (let d = 2; d <= 24; d++) {
    const n = Math.round(frac * d);
    if (Math.abs(frac * d - n) < 1e-6) return `${whole ? `${whole} ` : ''}${n}/${d}`;
  }
  return part.toFixed(2);
}

function PartyItems({ party, me, host, act, label }: { party: Party; me: PartyMember; host: PartyMember; act: Act; label: Label }) {
  const open = party.status === 'gathering';
  return (
    <section className="card party-items">
      <h2>The order</h2>
      <p className="muted small">
        {open
          ? 'Tap what you ate. People on the same piece split its price evenly; anything nobody picks goes on the host.'
          : party.split === 'even'
            ? `Split evenly: ${money(party.fare.total / party.members.length)} each.`
            : party.roulettePayerId
              ? 'The cards decided who pays.'
              : 'The order is locked.'}
      </p>
      <ul className="party-lines">
        {party.lines.map((l, i) => (
          <PartyLineRow key={l.itemId} party={party} line={l} me={me} host={host} open={open} act={act} label={label} i={i} />
        ))}
      </ul>
      <FareLines fare={party.fare} />
    </section>
  );
}

/**
 * One item. Each piece (bowl 1, bowl 2…) is split evenly between who tapped it, so in a party of n a piece goes down
 * to 1/n of its price. With more than one piece, a stepper picks how many you're having.
 */
function PartyLineRow({ party, line: l, me, host, open, act, label, i }: { party: Party; line: PartyLine; me: PartyMember; host: PartyMember; open: boolean; act: Act; label: Label; i: number }) {
  const partOf = (id: string) => l.pieces.reduce((sum, p) => sum + (p.includes(id) ? 1 / p.length : 0), 0);
  const eaters = party.members.filter((m) => partOf(m.id) > 0);
  const mine = partOf(me.id);
  const nobody = l.pieces.filter((p) => p.length === 0).length;
  const toggle = (piece: number, on: boolean) =>
    act(label(`${on ? 'Drop' : 'Claim'} ${l.name}${l.quantity > 1 ? ` #${piece + 1}` : ''}`), () => toggleClaim(party.code, l.itemId, piece));
  // "+" takes a piece nobody has, else joins the least shared one; "−" leaves the most shared one (your smallest part).
  const byCrowd = l.pieces.map((p, k) => ({ p, k })).sort((a, b) => a.p.length - b.p.length);
  const nextIn = byCrowd.find(({ p }) => !p.includes(me.id));
  const nextOut = byCrowd.filter(({ p }) => p.includes(me.id)).at(-1);

  const who = (id: string) => (id === me.id ? 'You' : party.members.find((m) => m.id === id)?.name);
  const summary =
    eaters.length === 0
      ? `${open ? 'Nobody yet' : 'Nobody picked it'} · ${host.name} covers it`
      : l.quantity === 1
        ? eaters.length === 1
          ? who(eaters[0].id)
          : `Split ${eaters.length} ways · ${money(l.price / eaters.length)} each`
        : [...eaters.map((m) => `${who(m.id)} ${portion(partOf(m.id))}`), ...(nobody ? [`${nobody} on ${host.name}`] : [])].join(' · ');

  return (
    <li className={`party-line rise ${mine ? 'is-mine' : ''}`} style={stagger(i)}>
      <div className="party-line-what">
        <strong>
          {l.quantity > 1 && `${l.quantity}× `}
          {l.name}
        </strong>
        <small className="muted">{summary}</small>
      </div>
      <span className="party-eaters">
        {eaters.map((m) => (
          <Monogram key={m.id} name={m.name} hue={hueOf(m.id)} size="sm" />
        ))}
      </span>
      <span className="party-line-cost">{money(l.price * l.quantity)}</span>
      {open && l.quantity === 1 && (
        <button type="button" className={`btn btn-sm party-claim ${mine ? 'btn-secondary' : 'btn-ghost'}`} aria-pressed={mine > 0} onClick={() => toggle(0, mine > 0)}>
          {mine ? (
            <>
              <Icon name="check" size={14} /> Mine
            </>
          ) : (
            "I'm eating this"
          )}
        </button>
      )}
      {open && l.quantity > 1 && (
        <span className="stepper party-stepper" role="group" aria-label={`How much ${l.name} you're having`}>
          <button type="button" aria-label={`Less ${l.name}`} disabled={!nextOut} onClick={() => nextOut && toggle(nextOut.k, true)}>
            −
          </button>
          <span aria-live="polite">{portion(mine)}</span>
          <button type="button" aria-label={`More ${l.name}`} disabled={!nextIn} onClick={() => nextIn && toggle(nextIn.k, false)}>
            +
          </button>
        </span>
      )}
      {l.quantity > 1 && (
        <div className="party-pieces">
          {l.pieces.map((p, k) => {
            const on = p.includes(me.id);
            const sharers = party.members.filter((m) => p.includes(m.id));
            return (
              <button
                key={k}
                type="button"
                className={`party-piece ${on ? 'is-mine' : ''} ${p.length === 0 ? 'is-free' : ''}`}
                aria-pressed={on}
                disabled={!open}
                aria-label={`${l.name} ${k + 1}: ${sharers.length ? sharers.map((m) => m.name).join(', ') : 'nobody yet'}`}
                onClick={() => toggle(k, on)}
              >
                <span className="party-piece-label">#{k + 1}</span>
                <span className="party-eaters">
                  {sharers.map((m) => (
                    <Monogram key={m.id} name={m.name} hue={hueOf(m.id)} size="sm" />
                  ))}
                </span>
                <small>{p.length > 1 ? `${money(l.price / p.length)} each` : p.length ? money(l.price) : open ? 'Tap to take' : 'On the host'}</small>
              </button>
            );
          })}
        </div>
      )}
    </li>
  );
}

function SharePanel({ party, me, host, act, label, onPaid }: { party: Party; me: PartyMember; host: PartyMember; act: Act; label: Label; onPaid: () => void }) {
  const payer = party.members.find((m) => m.id === party.roulettePayerId);
  const unpaid = party.members.filter((m) => !m.paid && m.share > 0).length;
  const shareNote =
    party.status !== 'paying'
      ? 'So far. It changes as people pick items.'
      : me.paid
        ? 'Paid. Thanks!'
        : me.share === 0
          ? payer
            ? `${payer.id === me.id ? 'You have' : `${payer.name} has`} it covered.`
            : 'Nothing to pay.'
          : 'Ready when you are.';

  return (
    <aside className="card party-share">
      <span className="eyebrow">Your share</span>
      <div className={`party-share-amount ${me.paid ? 'is-paid' : ''}`}>{money(me.share)}</div>
      <small className="muted">{shareNote}</small>

      <ul className="party-members">
        {party.members.map((m) => (
          <li key={m.id}>
            <Monogram name={m.name} hue={hueOf(m.id)} size="sm" />
            <span className="party-member-name">
              {m.id === me.id ? 'You' : m.name}
              {m.host && <span className="party-chip">Host</span>}
            </span>
            <span className="party-member-share">{money(m.share)}</span>
            {party.status === 'paying' && m.share > 0 && <span className={`party-chip ${m.paid ? 'is-ok' : ''}`}>{m.paid ? 'Paid' : 'Waiting'}</span>}
          </li>
        ))}
      </ul>

      {party.status === 'gathering' &&
        (me.host ? (
          <div className="party-actions">
            <ConfirmButton
              block
              label={
                <>
                  <Icon name="tag" size={16} /> Split by what we picked
                </>
              }
              question="Lock the order, each pays for what they picked?"
              onConfirm={() => act(label('Lock the split by items'), () => lockSplit(party.code, 'items'))}
            />
            <ConfirmButton
              block
              variant="secondary"
              disabled={party.members.length < 2}
              label={
                <>
                  <Icon name="users" size={16} /> Split evenly · {money(party.fare.total / party.members.length)} each
                </>
              }
              question={`Lock the order, everyone pays ${money(party.fare.total / party.members.length)}?`}
              onConfirm={() => act(label('Lock the split evenly'), () => lockSplit(party.code, 'even'))}
            />
            <ConfirmButton
              block
              variant="dark"
              disabled={party.members.length < 2}
              label={
                <>
                  <Icon name="card" size={16} /> Card roulette
                </>
              }
              question="Everyone has to accept, and one card pays everything. Start it?"
              onConfirm={() => act(label('Start the card roulette'), () => proposeRoulette(party.code))}
            />
            {party.members.length < 2 && <small className="muted center">Splitting evenly and the roulette need at least two people.</small>}
            <ConfirmButton
              block
              variant="danger"
              label="Cancel the party"
              question="Cancel this party for everyone?"
              onConfirm={() => act(label('Cancel'), () => cancelParty(party.code))}
            />
          </div>
        ) : (
          <p className="party-waiting">
            <span className="party-dots" aria-hidden="true">
              <i />
              <i />
              <i />
            </span>
            Waiting for {host.name} to lock the split or start the roulette.
          </p>
        ))}

      {party.status === 'paying' && me.share > 0 && !me.paid && <PayShare party={party} me={me} onPaid={onPaid} />}
      {party.status === 'paying' && (me.paid || me.share === 0) && (
        <p className="party-waiting">
          <span className="party-dots" aria-hidden="true">
            <i />
            <i />
            <i />
          </span>
          Waiting for {unpaid} more {unpaid === 1 ? 'payment' : 'payments'}. The order goes out once everyone paid.
        </p>
      )}

      <small className="muted party-fineprint">
        <Icon name="pin" size={14} /> Goes out under {host.name}'s name, to {party.deliveryAddress}.
      </small>
    </aside>
  );
}

/** Holds the member's share on their card, then confirms it (same two steps as the cart's checkout, #259). */
function PayShare({ party, me, onPaid }: { party: Party; me: PartyMember; onPaid: () => void }) {
  const [held, setHeld] = useState<PartyCheckout | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const what = `my share of party ${showCode(party.code)}`;

  async function start() {
    setError('');
    setBusy(true);
    try {
      setHeld(await send(`Check out ${what}`, () => startShareCheckout(party.code)));
    } catch (e) {
      setError(errorMessage(e));
    }
    setBusy(false);
  }

  async function pay(confirmCard?: ConfirmCard) {
    if (!held) return;
    setError('');
    setBusy(true);
    try {
      await send(`Pay ${what} (${money(held.amount)})`, async () => {
        await confirmCard?.();
        return confirmShare(party.code);
      });
      onPaid();
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  }

  return (
    <div className="party-pay">
      {error && (
        <p className="alert alert-error" role="alert">
          {error}
        </p>
      )}
      {!held ? (
        <button type="button" className="btn btn-dark btn-block btn-lg" disabled={busy} onClick={start}>
          <Icon name="card" size={16} /> {busy ? 'Getting the card form…' : `Pay my share · ${money(me.share)}`}
        </button>
      ) : held.clientSecret ? (
        <CardPayment clientSecret={held.clientSecret} label={busy ? 'Paying…' : `Pay ${money(held.amount)}`} question={`Pay ${money(held.amount)} for your share?`} disabled={busy} onPay={pay} />
      ) : (
        // shortcut: the stub has no Stripe secret, so there's no card field; goes away with POST /parties/{code}/checkout.
        <>
          <ConfirmButton block variant="dark" label={busy ? 'Paying…' : `Pay ${money(held.amount)}`} question={`Pay ${money(held.amount)} for your share?`} disabled={busy} onConfirm={() => pay()} />
          <small className="muted center">No card field until the party backend is connected.</small>
        </>
      )}
    </div>
  );
}

function RouletteVote({ party, me, act, label }: { party: Party; me: PartyMember; act: Act; label: Label }) {
  const waiting = party.members.filter((m) => !m.acceptedRoulette).length;
  return (
    <section className="card party-roulette rise">
      <header>
        <span className="eyebrow">Card roulette</span>
        <h2>One card pays for everyone</h2>
        <p className="muted">Everyone has to accept. One "no" calls it off and you're back to splitting.</p>
      </header>
      <CardRoulette members={party.members} />
      <ul className="roulette-votes">
        {party.members.map((m) => (
          <li key={m.id} className={m.acceptedRoulette ? 'is-in' : ''}>
            <Monogram name={m.name} hue={hueOf(m.id)} size="sm" />
            {m.id === me.id ? 'You' : m.name}
            <span className={`party-chip ${m.acceptedRoulette ? 'is-ok' : ''}`}>{m.acceptedRoulette ? "I'm in" : 'Deciding'}</span>
          </li>
        ))}
      </ul>
      {!me.acceptedRoulette ? (
        <div className="roulette-choice">
          <button type="button" className="btn btn-ghost" onClick={() => act(label('Decline the roulette'), () => declineRoulette(party.code))}>
            No thanks
          </button>
          <button type="button" className="btn btn-primary btn-lg" onClick={() => act(label('Accept the roulette'), () => acceptRoulette(party.code))}>
            <Icon name="card" size={16} /> I'm in, spin it
          </button>
        </div>
      ) : (
        <p className="party-waiting center">
          <span className="party-dots" aria-hidden="true">
            <i />
            <i />
            <i />
          </span>
          Waiting for {waiting} more. The cards spin once everyone is in.
        </p>
      )}
    </section>
  );
}

function Celebration({ party, host, isHost, meId }: { party: Party; host: PartyMember; isHost: boolean; meId: string }) {
  const payer = party.members.find((m) => m.id === party.roulettePayerId);
  return (
    <section className="card party-done rise">
      <div className="confetti" aria-hidden="true">
        {Array.from({ length: 40 }, (_, i) => (
          <i key={i} style={{ '--x': `${(i * 37) % 100}%`, '--d': `${((i * 53) % 20) / 10}s`, '--h': (i * 47) % 360, '--r': `${(i * 71) % 360}deg` } as CSSProperties} />
        ))}
      </div>
      <span className="party-done-icon">
        <Icon name="check" size={34} />
      </span>
      <h2>Order placed!</h2>
      <p>
        Everyone paid, so {party.restaurantName} has it now, under {host.name}'s name.
        {payer && (payer.id === meId ? ' The cards picked you, so this one was on you.' : ` Thanks to ${payer.name}, who the cards picked.`)}
      </p>
      {isHost ? (
        <Link className="btn btn-primary" to="/customer/orders">
          Track it in Delivery
        </Link>
      ) : (
        <p className="muted">{host.name} can track the delivery.</p>
      )}
    </section>
  );
}

/** Soft coloured lights drifting behind a party header. */
function PartyLights() {
  return (
    <span className="party-lights" aria-hidden="true">
      <i />
      <i />
      <i />
    </span>
  );
}
