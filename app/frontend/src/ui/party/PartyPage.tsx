import { useEffect, useState, type CSSProperties, type FormEvent } from 'react';
import { Link, Navigate, Route, Routes, useNavigate, useParams } from 'react-router-dom';
import QRCode from 'qrcode';
import type { Party, PartyCheckout, PartyMember, PartyVote } from '../../domain';
import { useUser } from '../../session';
import { ConfirmButton, Empty, LoadError, Loading, money, Monogram, stagger, useLoad, usePolling } from '../common';
import { errorMessage, send } from '../dashboard/dashboardApi';
import { Icon } from '../icons';
import { getRestaurantMenu } from '../ord/ordApi';
import CardPayment, { type ConfirmCard } from '../prc/CardPayment';
import FareLines from '../prc/FareLines';
import CardRoulette, { hueOf } from './CardRoulette';
import { cancelParty, confirmShare, getMyParty, getParty, joinParty, leaveParty, lockParty, normalizeCode, partyLink, setPartyLine, setReady, startShareCheckout } from './partyApi';

/** "K7Q2MX" -> "K7Q-2MX", easier to read out loud. */
const showCode = (code: string) => code.replace(/^(.{3})(.+)$/, '$1-$2');

/** Party mode (/customer/party): join with a code, or the party room itself (also what the share link and QR code open). */
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
          <p>One order, one delivery, everyone at the table. Each person adds what they want and pays for it, or let the card roulette pick who pays for all of it.</p>
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
              The host sees it at the top of their party. A link or QR code from the host opens the party directly.
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
              <strong>Share the code, link or QR code.</strong> Friends log in, join and add what they want from the same restaurant.
            </li>
            <li>
              <strong>Get ready and vote.</strong> Pay your own, or the card roulette if everyone votes for it. You lock the order, everyone pays, and it goes out.
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
const doNothing = () => {};

function PartyRoom() {
  const { code = '' } = useParams();
  const user = useUser();
  const { data: party, error, reload } = useLoad(() => getParty(code), [code]);
  // A code that doesn't exist stops polling (no error every 2 seconds); "Try again" still reloads.
  usePolling(error && !party ? doNothing : reload, 2000);
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

  if (error && !party)
    return (
      <>
        <LoadError what="the party" message={error} onRetry={reload} />
        <p className="center">
          <Link to="/customer/party">Back to party mode</Link>
        </p>
      </>
    );
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
          {party.cancelReason && (
            <>
              {party.cancelReason}
              <br />
            </>
          )}
          <Link to="/customer/party">Back to party mode</Link>
        </Empty>
      ) : !me ? (
        <section className="card party-invite rise">
          <h2>{host.name} invited you</h2>
          {party.status === 'gathering' ? (
            <>
              <p className="muted">Join to add what you want from {party.restaurantName}. You only pay for your own items.</p>
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
          {party.roulettePayerId && (
            <section className="card party-roulette rise">
              <CardRoulette members={party.members} payerId={party.roulettePayerId} spin={!spun.has(`${party.code}:${me.id}`)} onDone={() => spun.add(`${party.code}:${me.id}`)} />
            </section>
          )}
          <div className="party-grid">
            <PartyItems party={party} me={me} act={act} label={label} />
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
  const [copied, setCopied] = useState<'code' | 'link' | ''>('');
  const [showQr, setShowQr] = useState(false);
  const orderUndone = party.orderStatus === 'rejected' || party.orderStatus === 'cancelled' || party.orderStatus === 'refunded';
  const step = party.status === 'cancelled' || orderUndone ? -1 : party.status === 'placed' ? 2 : party.status === 'paying' ? 1 : 0;
  const open = party.status === 'gathering';

  async function copy(what: 'code' | 'link') {
    try {
      await navigator.clipboard.writeText(what === 'code' ? showCode(party.code) : partyLink(party.code));
      setCopied(what);
      setTimeout(() => setCopied(''), 1800);
    } catch {
      setCopied(''); // no clipboard access: the code is on screen anyway
    }
  }

  return (
    <section className="party-hero rise">
      <PartyLights />
      <div className="party-hero-main">
        <span className="eyebrow">Party at {party.restaurantName}</span>
        <h1>{host.name}'s party</h1>
        <ol className="party-steps" aria-label="Party progress">
          {['Add your food', 'Pay', 'Order placed'].map((s, i) => (
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
        <span className="party-share-buttons">
          <button type="button" className="btn btn-sm party-copy" onClick={() => copy('code')}>
            <Icon name={copied === 'code' ? 'check' : 'copy'} size={14} /> {copied === 'code' ? 'Copied' : 'Copy code'}
          </button>
          {open && (
            <>
              <button type="button" className="btn btn-sm party-copy" onClick={() => copy('link')}>
                <Icon name={copied === 'link' ? 'check' : 'copy'} size={14} /> {copied === 'link' ? 'Copied' : 'Copy link'}
              </button>
              <button type="button" className="btn btn-sm party-copy" aria-expanded={showQr} onClick={() => setShowQr(!showQr)}>
                <Icon name="grid" size={14} /> {showQr ? 'Hide QR' : 'QR code'}
              </button>
            </>
          )}
        </span>
        {open && showQr && <PartyQr code={party.code} />}
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

/** The share link as a QR code, drawn in the browser (the link is never sent to another site). */
function PartyQr({ code }: { code: string }) {
  const [image, setImage] = useState('');
  useEffect(() => {
    let current = true;
    QRCode.toDataURL(partyLink(code), { margin: 1, width: 360 }).then(
      (url) => current && setImage(url),
      () => current && setImage(''),
    );
    return () => {
      current = false;
    };
  }, [code]);

  return (
    <figure className="party-qr rise">
      {image ? <img src={image} alt={`QR code to join party ${showCode(code)}`} width={180} height={180} /> : <span className="party-qr-blank" />}
      <figcaption>Scan to join. You'll log in first.</figcaption>
    </figure>
  );
}

/** Left side: what I'm adding from the menu (while gathering), then everyone's items, then the fare. */
function PartyItems({ party, me, act, label }: { party: Party; me: PartyMember; act: Act; label: Label }) {
  const open = party.status === 'gathering';
  const byMember = party.members
    .map((m) => ({ member: m, lines: party.lines.filter((l) => l.memberId === m.id) }))
    .filter(({ lines }) => lines.length > 0);

  return (
    <section className="card party-items">
      {open && <PartyMenu party={party} me={me} act={act} label={label} />}

      <h2>The order</h2>
      <p className="muted small">
        {open
          ? 'Everyone pays for what they added, plus an even part of the fees.'
          : party.mode === 'roulette'
            ? 'The cards decided who pays for all of it.'
            : 'The order is locked. Each person pays for what they added.'}
      </p>
      {byMember.length === 0 ? (
        <p className="muted">Nothing added yet.</p>
      ) : (
        <ul className="party-lines">
          {byMember.map(({ member, lines }, i) => (
            <li key={member.id} className={`party-line party-person rise ${member.id === me.id ? 'is-mine' : ''}`} style={stagger(i)}>
              <div className="party-person-head">
                <Monogram name={member.name} hue={hueOf(member.id)} size="sm" />
                <strong>{member.id === me.id ? 'You' : member.name}</strong>
                <span className="party-line-cost">{money(lines.reduce((sum, l) => sum + l.price * l.quantity, 0))}</span>
              </div>
              <ul className="party-person-items">
                {lines.map((l) => (
                  <li key={l.itemId}>
                    <span>
                      {l.quantity}× {l.name} {!l.available && open && <span className="party-chip">Sold out, remove it</span>}
                    </span>
                    <span className="muted">{money(l.price * l.quantity)}</span>
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
      )}
      <FareLines fare={party.fare} />
    </section>
  );
}

/** The host's restaurant menu with a stepper per item: how many I want. Changing it makes me not ready again. */
function PartyMenu({ party, me, act, label }: { party: Party; me: PartyMember; act: Act; label: Label }) {
  const { data, error, reload } = useLoad(() => getRestaurantMenu(party.restaurantId), [party.restaurantId]);
  const mine = (itemId: string) => party.lines.find((l) => l.memberId === me.id && l.itemId === itemId)?.quantity ?? 0;
  const set = (itemId: string, name: string, quantity: number) =>
    act(label(quantity === 0 ? `Remove ${name}` : `${name} × ${quantity}`), () => setPartyLine(party.code, itemId, quantity));

  if (error && !data) return <LoadError what="the menu" message={error} onRetry={reload} />;
  if (!data) return <Loading what="the menu" />;
  const items = data[1].filter((m) => m.status === 'available' || mine(m.id) > 0);

  return (
    <div className="party-menu">
      <h2>Add what you want</h2>
      <p className="muted small">From {party.restaurantName}. Only you pay for what you add here.</p>
      <ul className="party-menu-list">
        {items.map((m) => {
          const qty = mine(m.id);
          return (
            <li key={m.id} className={qty ? 'is-mine' : ''}>
              <span className="party-line-what">
                <strong>{m.name}</strong>
                <small className="muted">{money(m.price)}</small>
              </span>
              <span className="stepper party-stepper" role="group" aria-label={`How many ${m.name}`}>
                <button type="button" aria-label={`Less ${m.name}`} disabled={qty === 0} onClick={() => set(m.id, m.name, qty - 1)}>
                  −
                </button>
                <span aria-live="polite">{qty}</span>
                <button type="button" aria-label={`More ${m.name}`} disabled={m.status !== 'available'} onClick={() => set(m.id, m.name, qty + 1)}>
                  +
                </button>
              </span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

/** Right side: my share, who's ready, my ready + vote, the host's lock, then paying. */
function SharePanel({ party, me, host, act, label, onPaid }: { party: Party; me: PartyMember; host: PartyMember; act: Act; label: Label; onPaid: () => void }) {
  const navigate = useNavigate();
  const payer = party.members.find((m) => m.id === party.roulettePayerId);
  const unpaid = party.members.filter((m) => !m.paid && m.share > 0).length;
  const notReady = party.members.filter((m) => !m.ready);
  const hasItems = party.lines.some((l) => l.memberId === me.id);
  const everyoneRoulette = party.members.length > 1 && party.members.every((m) => m.vote === 'roulette');
  const shareNote =
    party.status !== 'paying'
      ? 'So far: your items plus your part of the fees.'
      : me.paid
        ? 'Paid. Thanks!'
        : me.share === 0
          ? payer
            ? `${payer.id === me.id ? 'You have' : `${payer.name} has`} it covered.`
            : 'Nothing to pay.'
          : 'Ready when you are.';
  const ready = (vote: PartyVote) => act(label(vote === 'roulette' ? "Ready, I'm in for the roulette" : 'Ready, I pay mine'), () => setReady(party.code, true, vote));

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
            {party.status === 'gathering' && (
              <span className={`party-chip ${m.ready ? 'is-ok' : ''}`}>{m.ready ? (m.vote === 'roulette' ? 'Ready · roulette' : 'Ready') : 'Picking'}</span>
            )}
            {party.status === 'paying' && m.share > 0 && <span className={`party-chip ${m.paid ? 'is-ok' : ''}`}>{m.paid ? 'Paid' : 'Waiting'}</span>}
          </li>
        ))}
      </ul>

      {party.status === 'gathering' && (
        <div className="party-actions">
          {!me.ready ? (
            <>
              <button type="button" className="btn btn-primary btn-block" disabled={!hasItems} onClick={() => ready('ordinary')}>
                <Icon name="check" size={16} /> I'm ready · I pay mine
              </button>
              <button type="button" className="btn btn-dark btn-block" disabled={!hasItems || party.members.length < 2} onClick={() => ready('roulette')}>
                <Icon name="card" size={16} /> I'm ready · card roulette
              </button>
              <small className="muted center">
                {!hasItems ? 'Add at least one item to get ready.' : 'The roulette happens only if everyone votes for it: one random card pays for all of it.'}
              </small>
            </>
          ) : (
            <button type="button" className="btn btn-ghost btn-block" onClick={() => act(label('Not ready'), () => setReady(party.code, false))}>
              I'm not ready anymore
            </button>
          )}

          {me.host ? (
            <>
              <ConfirmButton
                block
                variant={everyoneRoulette ? 'dark' : undefined}
                disabled={notReady.length > 0}
                label={
                  <>
                    <Icon name={everyoneRoulette ? 'card' : 'tag'} size={16} /> {everyoneRoulette ? 'Lock and spin the cards' : 'Lock the order'}
                  </>
                }
                question={everyoneRoulette ? 'Everyone voted roulette: one card pays everything. Lock it?' : 'Lock the order? Each person pays for what they added.'}
                onConfirm={() => act(label('Lock the order'), () => lockParty(party.code))}
              />
              {notReady.length > 0 && <small className="muted center">Waiting for {notReady.map((m) => (m.id === me.id ? 'you' : m.name)).join(', ')} to be ready.</small>}
              <ConfirmButton block variant="danger" label="Cancel the party" question="Cancel this party for everyone?" onConfirm={() => act(label('Cancel'), () => cancelParty(party.code))} />
            </>
          ) : (
            <>
              {me.ready && (
                <p className="party-waiting">
                  <span className="party-dots" aria-hidden="true">
                    <i />
                    <i />
                    <i />
                  </span>
                  Waiting for {host.name} to lock the order.
                </p>
              )}
              <ConfirmButton block variant="secondary" label="Leave the party" question="Leave this party? Your items are removed." onConfirm={() =>
                  act(label('Leave'), async () => {
                    await leaveParty(party.code);
                    navigate('/customer/party'); // back to the lobby, free to join another party
                  })
                }
              />
            </>
          )}
        </div>
      )}

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

      {party.status === 'paying' && (
        // Anyone can call it off before the order goes out (cards are only held), so nobody is stuck waiting on one person.
        <ConfirmButton
          block
          variant="danger"
          label="Call off the party"
          question="Call off the party for everyone? Every card hold is released and nobody is charged."
          onConfirm={() => act(label('Call off'), () => cancelParty(party.code))}
        />
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

  async function pay(confirmCard: ConfirmCard) {
    if (!held) return;
    setError('');
    setBusy(true);
    try {
      await send(`Pay ${what} (${money(held.amount)})`, async () => {
        await confirmCard();
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
      ) : (
        <CardPayment clientSecret={held.clientSecret} label={busy ? 'Paying…' : `Pay ${money(held.amount)}`} question={`Pay ${money(held.amount)} for your share?`} disabled={busy} onPay={pay} />
      )}
    </div>
  );
}

function Celebration({ party, host, isHost, meId }: { party: Party; host: PartyMember; isHost: boolean; meId: string }) {
  const payer = party.members.find((m) => m.id === party.roulettePayerId);
  // The order was placed, but the restaurant can still reject it (or the host cancel it, or the operator refund it).
  if (party.orderStatus === 'rejected' || party.orderStatus === 'cancelled' || party.orderStatus === 'refunded') {
    const what = { rejected: `${party.restaurantName} rejected the order`, cancelled: 'The order was cancelled', refunded: 'The order was refunded' }[party.orderStatus];
    return (
      <Empty icon="alert" title={what}>
        {party.orderStatus === 'refunded' ? 'Everyone who paid gets their share back.' : 'Every card hold was released, nobody was charged.'}
        <br />
        <Link to="/customer/party">Back to party mode</Link>
      </Empty>
    );
  }
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
      <h2>{party.orderStatus === 'delivered' ? 'Delivered!' : 'Order placed!'}</h2>
      <p>
        {party.orderStatus === 'delivered'
          ? `${party.restaurantName}'s order reached ${host.name}. Enjoy!`
          : `Everyone paid, so ${party.restaurantName} has it now, under ${host.name}'s name.`}
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
