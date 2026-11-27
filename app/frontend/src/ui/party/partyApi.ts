// partyApi (ui.party): every party-mode call to the backend (phase4-party).
// The party routes don't exist yet (lekerj builds them), so these are stubs holding the parties in this browser tab:
// a page reload forgets them, and only accounts that sign in on this tab can join. Each function is one future REST call;
// only its body changes when the backend is connected. Like the backend, the stub reads who calls from the session.
import type { Party, PartyCheckout, PartyMember, PartySplit } from '../../domain';
import { getRestaurantMenu } from '../ord/ordApi';
import type { CartOrder } from '../prc/prcApi';
import { currentSession } from '../reg/regApi';

/** "k7q 2mx" -> "K7Q2MX": what the code field sends. */
export const normalizeCode = (code: string) => code.toUpperCase().replace(/[^A-Z0-9]/g, '');

// ---------- Stub state and rules, removed with the stubs (the backend owns all of this) ----------

const parties = new Map<string, Party>();
const CODE_CHARS = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'; // no 0/O or 1/I/L, so a code read out loud is typed right
const cents = (n: number) => Math.round(n * 100) / 100;
// Callers get a copy, like a JSON response: React only re-renders on a new object.
const copy = (p: Party): Party => structuredClone(p);

async function customer() {
  const user = await currentSession();
  if (user.role !== 'customer') throw new Error('Only customers can join a party');
  return user;
}

function find(code: string): Party {
  const party = parties.get(normalizeCode(code));
  if (!party) throw new Error('No party with that code');
  return party;
}

function memberOf(party: Party, userId: string): PartyMember {
  const member = party.members.find((m) => m.id === userId);
  if (!member) throw new Error("You're not in this party");
  return member;
}

function requireHost(party: Party, userId: string) {
  if (party.hostId !== userId) throw new Error('Only the host can do that');
}

function requireStatus(party: Party, status: Party['status'], message: string) {
  if (party.status !== status) throw new Error(message);
}

/**
 * items: each piece is split evenly between who claimed it, the host pays unclaimed pieces, fees are split evenly.
 * even: everyone pays the same.
 */
function splitShares(party: Party, split: PartySplit = 'items') {
  const shares = new Map(party.members.map((m) => [m.id, 0]));
  const add = (id: string, amount: number) => shares.set(id, (shares.get(id) ?? 0) + amount);
  if (split === 'even') party.members.forEach((m) => add(m.id, party.fare.total / party.members.length));
  else {
    for (const line of party.lines) {
      for (const sharers of line.pieces) {
        if (sharers.length === 0) add(party.hostId, line.price);
        else sharers.forEach((id) => add(id, line.price / sharers.length));
      }
    }
    const fees = party.fare.total - party.fare.subtotal;
    party.members.forEach((m) => add(m.id, fees / party.members.length));
  }
  party.members.forEach((m) => (m.share = cents(shares.get(m.id) ?? 0)));
  // Rounding leftovers (a cent or two) go to the host, so the shares always add up to the total.
  const host = party.members.find((m) => m.host)!;
  host.share = cents(host.share + party.fare.total - party.members.reduce((sum, m) => sum + m.share, 0));
}

function placeIfEveryonePaid(party: Party) {
  if (party.members.every((m) => m.paid || m.share === 0)) party.status = 'placed';
}

// ---------- The calls ----------

/**
 * TODO(party): POST /parties { restaurantId, lines, instructions, deliveryAddress } (customer). The host turns their cart
 * into a party instead of paying; the backend prices it like checkout (R-PRC-01) and answers the party with its code.
 */
export async function startParty(cart: CartOrder): Promise<Party> {
  const host = await customer();
  const [restaurant, menu] = await getRestaurantMenu(cart.restaurantId);
  let code = '';
  do code = Array.from({ length: 6 }, () => CODE_CHARS[Math.floor(Math.random() * CODE_CHARS.length)]).join('');
  while (parties.has(code));
  const lines = cart.lines.map((l) => {
    const item = menu.find((m) => m.id === l.itemId);
    if (!item) throw new Error('An item in your cart is no longer on the menu');
    return { itemId: item.id, name: item.name, price: item.price, quantity: l.quantity, pieces: Array.from({ length: l.quantity }, () => []) };
  });
  const subtotal = cents(lines.reduce((sum, l) => sum + l.price * l.quantity, 0));
  const party: Party = {
    code,
    restaurantId: restaurant.id,
    restaurantName: restaurant.name,
    hostId: host.id,
    deliveryAddress: cart.deliveryAddress.trim(),
    status: 'gathering',
    members: [{ id: host.id, name: host.name, host: true, share: 0, paid: false, acceptedRoulette: false }],
    lines,
    // The stub can't price fees; the backend's fare (delivery, service, surcharge) replaces this.
    fare: { subtotal, deliveryFee: 0, serviceFee: 0, surcharge: 0, total: subtotal },
  };
  splitShares(party);
  parties.set(code, party);
  return copy(party);
}

/** TODO(party): GET /parties/{code} (any customer with the code). Polled by the party room. */
export async function getParty(code: string): Promise<Party> {
  await customer();
  return copy(find(code));
}

/** TODO(party): GET /parties/mine: the party the customer is in that isn't placed or cancelled yet, if any. */
export async function getMyParty(): Promise<Party | undefined> {
  const me = await customer();
  const mine = [...parties.values()].find((p) => !['placed', 'cancelled'].includes(p.status) && p.members.some((m) => m.id === me.id));
  return mine && copy(mine);
}

/** TODO(party): POST /parties/{code}/join. Refused once the host locked the order. Joining twice is fine. */
export async function joinParty(code: string): Promise<Party> {
  const me = await customer();
  const party = find(code);
  if (!party.members.some((m) => m.id === me.id)) {
    requireStatus(party, 'gathering', 'This party already locked its order');
    party.members.push({ id: me.id, name: me.name, host: false, share: 0, paid: false, acceptedRoulette: false });
    splitShares(party);
  }
  return copy(party);
}

/**
 * TODO(party): POST /parties/{code}/lines/{itemId}/pieces/{piece}/claim: "I'm eating (part of) this piece" on, or off
 * again (toggles). `piece` is 0 to quantity - 1; everyone on a piece shares its price evenly.
 */
export async function toggleClaim(code: string, itemId: string, piece: number): Promise<Party> {
  const me = await customer();
  const party = find(code);
  memberOf(party, me.id);
  requireStatus(party, 'gathering', 'The order is locked');
  const line = party.lines.find((l) => l.itemId === itemId);
  const sharers = line?.pieces[piece];
  if (!line || !sharers) throw new Error('That item is not in this party');
  line.pieces[piece] = sharers.includes(me.id) ? sharers.filter((id) => id !== me.id) : [...sharers, me.id];
  splitShares(party);
  return copy(party);
}

/** TODO(party): POST /parties/{code}/split { split: 'items' | 'even' } (host): locks the order; each member now pays their share. */
export async function lockSplit(code: string, split: PartySplit): Promise<Party> {
  const me = await customer();
  const party = find(code);
  requireHost(party, me.id);
  requireStatus(party, 'gathering', 'The order is already locked');
  splitShares(party, split);
  party.split = split;
  party.status = 'paying';
  placeIfEveryonePaid(party);
  return copy(party);
}

/** TODO(party): POST /parties/{code}/roulette (host): asks everyone to accept the card roulette. The host has accepted. */
export async function proposeRoulette(code: string): Promise<Party> {
  const me = await customer();
  const party = find(code);
  requireHost(party, me.id);
  requireStatus(party, 'gathering', 'The order is already locked');
  if (party.members.length < 2) throw new Error('The roulette needs at least two people');
  party.status = 'roulette';
  party.members.forEach((m) => (m.acceptedRoulette = m.host));
  return copy(party);
}

/**
 * TODO(party): POST /parties/{code}/roulette/accept. Once everyone accepted, the backend picks the payer at random,
 * who pays the whole order (`roulettePayerId`), and the party moves to paying.
 */
export async function acceptRoulette(code: string): Promise<Party> {
  const me = await customer();
  const party = find(code);
  requireStatus(party, 'roulette', 'There is no roulette to accept');
  memberOf(party, me.id).acceptedRoulette = true;
  if (party.members.every((m) => m.acceptedRoulette)) {
    const payer = party.members[Math.floor(Math.random() * party.members.length)];
    party.roulettePayerId = payer.id;
    party.members.forEach((m) => (m.share = m.id === payer.id ? party.fare.total : 0));
    party.status = 'paying';
  }
  return copy(party);
}

/** TODO(party): POST /parties/{code}/roulette/decline: one "no" calls the roulette off and the party goes back to gathering. */
export async function declineRoulette(code: string): Promise<Party> {
  const me = await customer();
  const party = find(code);
  requireStatus(party, 'roulette', 'There is no roulette to decline');
  memberOf(party, me.id);
  party.status = 'gathering';
  party.members.forEach((m) => (m.acceptedRoulette = false));
  splitShares(party);
  return copy(party);
}

/** TODO(party): POST /parties/{code}/cancel (host): only before anyone paid. */
export async function cancelParty(code: string): Promise<Party> {
  const me = await customer();
  const party = find(code);
  requireHost(party, me.id);
  if (party.status === 'placed' || party.members.some((m) => m.paid)) throw new Error('Someone already paid, so the party can no longer be cancelled');
  party.status = 'cancelled';
  return copy(party);
}

/**
 * TODO(party): POST /parties/{code}/checkout: holds this member's share on their card, like the cart's checkout (#259),
 * and answers the amount with the Stripe client secret the card field confirms against.
 */
export async function startShareCheckout(code: string): Promise<PartyCheckout> {
  const me = await customer();
  const party = find(code);
  requireStatus(party, 'paying', 'The order is not ready to pay yet');
  const member = memberOf(party, me.id);
  if (member.paid) throw new Error('You already paid your share');
  if (member.share === 0) throw new Error('You have nothing to pay');
  return { amount: member.share };
}

/**
 * TODO(party): POST /parties/{code}/pay: once the card is confirmed. When the last share is paid, the backend places
 * the order under the host's name (R-ORD) and sets `orderId`.
 */
export async function confirmShare(code: string): Promise<Party> {
  const me = await customer();
  const party = find(code);
  requireStatus(party, 'paying', 'The order is not ready to pay yet');
  const member = memberOf(party, me.id);
  if (member.paid) throw new Error('You already paid your share');
  member.paid = true;
  placeIfEveryonePaid(party);
  return copy(party);
}
