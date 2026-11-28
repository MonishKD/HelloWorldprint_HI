// partyApi (ui.party): every party-mode call to the backend (PartyController, /api/parties).
// Each function is one REST call; adapters.ts turns the backend's JSON into the types in domain.ts.
// The backend reads who calls from the session, so a friend opening the share link or QR code has to log in first.
import type { Party, PartyCheckout, PartyVote } from '../../domain';
import { toBackendEnum, toParty, toPartyCheckout } from '../adapters';
import { request } from '../http';
import type { CartOrder } from '../prc/prcApi';

/** "k7q 2mx" -> "K7Q2MX": what the code field sends. */
export const normalizeCode = (code: string) => code.toUpperCase().replace(/[^A-Z0-9]/g, '');

/** The link friends open (or scan as a QR code) to land in the party room. Signed out, they log in first and come back. */
export const partyLink = (code: string) => `${window.location.origin}/customer/party/${normalizeCode(code)}`;

const path = (code: string, action = '') => `/parties/${normalizeCode(code)}${action}`;
const post = async (code: string, action: string, body?: unknown) => toParty(await request(path(code, action), { method: 'POST', body }));

/** POST /parties: the host's cart becomes a party (the cart is the host's own items). Answers the party with its code. */
export async function startParty(cart: CartOrder): Promise<Party> {
  const body = { ...cart, instructions: cart.instructions.trim(), deliveryAddress: cart.deliveryAddress.trim() };
  return toParty(await request('/parties', { method: 'POST', body }));
}

/** GET /parties/{code} (any signed-in customer with the code). Polled by the party room. */
export async function getParty(code: string): Promise<Party> {
  return toParty(await request(path(code)));
}

/** GET /parties/mine: the party the customer is in that isn't placed or cancelled yet, if any. */
export async function getMyParty(): Promise<Party | undefined> {
  const party = await request<unknown>('/parties/mine');
  return party ? toParty(party) : undefined;
}

/** POST /parties/{code}/join. Refused once the host locked the order. Joining twice is fine. */
export const joinParty = (code: string) => post(code, '/join');

/** POST /parties/{code}/lines { itemId, quantity }: how many of this item I want (0 removes it). Makes me not ready again. */
export const setPartyLine = (code: string, itemId: string, quantity: number) => post(code, '/lines', { itemId, quantity });

/** POST /parties/{code}/ready { ready, vote }: ready with my vote, or not ready anymore. */
export const setReady = (code: string, ready: boolean, vote?: PartyVote) => post(code, '/ready', { ready, vote: vote ? toBackendEnum(vote) : null });

/** POST /parties/{code}/lock (host, everyone ready): fixes each share; the roulette spins if everyone voted for it. */
export const lockParty = (code: string) => post(code, '/lock');

/** POST /parties/{code}/leave: a member (not the host) leaves while the party is gathering. */
export const leaveParty = (code: string) => post(code, '/leave');

/** POST /parties/{code}/cancel: the host while gathering, any member while paying. Releases every card hold. */
export const cancelParty = (code: string) => post(code, '/cancel');

/** POST /parties/{code}/checkout: holds my share on my card and answers the Stripe client secret for the card field. */
export async function startShareCheckout(code: string): Promise<PartyCheckout> {
  return toPartyCheckout(await request(path(code, '/checkout'), { method: 'POST' }));
}

/** POST /parties/{code}/pay: once the card is confirmed. The last share places the order under the host's name. */
export const confirmShare = (code: string) => post(code, '/pay');
