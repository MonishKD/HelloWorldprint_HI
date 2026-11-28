package com.fortunategoods.application.party;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.orders.placement.PlaceOrderReqJSON;
import com.fortunategoods.application.party.gathering.CancelPartyCommand;
import com.fortunategoods.application.party.gathering.JoinPartyCommand;
import com.fortunategoods.application.party.gathering.LeavePartyCommand;
import com.fortunategoods.application.party.gathering.LockPartyCommand;
import com.fortunategoods.application.party.gathering.SetPartyLineCommand;
import com.fortunategoods.application.party.gathering.SetReadyCommand;
import com.fortunategoods.application.party.gathering.StartPartyCommand;
import com.fortunategoods.application.party.payment.PartyCheckoutCommand;
import com.fortunategoods.application.party.payment.PayPartyShareCommand;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.technical.security.SessionValidator;

/*
    REST endpoints for party mode (phase4 extra feature). Every route needs a signed-in customer, so someone who
    opens a share link or scans the QR code has to log in first.
      POST /api/parties                    same body as placing an order -> the new party with its code
      GET  /api/parties/mine               the party I'm in right now (data is null when none)
      GET  /api/parties/{code}             the party (polled by the party room)
      POST /api/parties/{code}/join
      POST /api/parties/{code}/leave      a member (not the host) leaves while gathering
      POST /api/parties/{code}/lines       { itemId, quantity }   my quantity of one item, 0 removes it
      POST /api/parties/{code}/ready       { ready, vote }        vote is ORDINARY or ROULETTE
      POST /api/parties/{code}/lock        host: everyone is ready, fix the shares
      POST /api/parties/{code}/cancel      gathering: the host; paying: any member (only holds, nobody is charged)
      POST /api/parties/{code}/checkout    hold my share on my card -> { amountCents, clientSecret }
      POST /api/parties/{code}/pay         after the card is confirmed; the last payment places the order
    All of them go through CommandInvoker, so they are checked and shown in the console like every other command.
*/
@RestController
@RequestMapping("/api/parties")
public class PartyController {

    private final CommandInvoker invoker;
    private final PartyService partyService;
    private final SessionValidator sessionValidator; // gives the session of the signed-in user

    public PartyController(CommandInvoker invoker, PartyService partyService, SessionValidator sessionValidator) {
        this.invoker = invoker;
        this.partyService = partyService;
        this.sessionValidator = sessionValidator;
    }

    @PostMapping
    public ResponseEntity<CommandSuccess<PartyResponse>> start(@RequestBody PlaceOrderReqJSON req) {
        Session session = sessionValidator.currentSession();
        StartPartyCommand command = new StartPartyCommand(partyService, session, req.getRestaurantId(),
                req.toRequestedItems(), req.getInstructions(), req.getDeliveryAddress());
        return ResponseEntity.ok(invoker.process(command, session));
    }

    // "/mine" is matched before "/{code}" because Spring prefers the exact path.
    @GetMapping("/mine")
    public ResponseEntity<CommandSuccess<PartyResponse>> mine() {
        Session session = sessionValidator.currentSession();
        return ResponseEntity.ok(invoker.process(new GetMyPartyCommand(partyService, session), session));
    }

    @GetMapping("/{code}")
    public ResponseEntity<CommandSuccess<PartyResponse>> get(@PathVariable String code) {
        Session session = sessionValidator.currentSession();
        return ResponseEntity.ok(invoker.process(new GetPartyCommand(partyService, session, code), session));
    }

    @PostMapping("/{code}/join")
    public ResponseEntity<CommandSuccess<PartyResponse>> join(@PathVariable String code) {
        Session session = sessionValidator.currentSession();
        return ResponseEntity.ok(invoker.process(new JoinPartyCommand(partyService, session, code), session));
    }

    @PostMapping("/{code}/leave")
    public ResponseEntity<CommandSuccess<PartyResponse>> leave(@PathVariable String code) {
        Session session = sessionValidator.currentSession();
        return ResponseEntity.ok(invoker.process(new LeavePartyCommand(partyService, session, code), session));
    }

    @PostMapping("/{code}/lines")
    public ResponseEntity<CommandSuccess<PartyResponse>> setLine(@PathVariable String code, @RequestBody SetPartyLineReqJSON req) {
        Session session = sessionValidator.currentSession();
        SetPartyLineCommand command = new SetPartyLineCommand(partyService, session, code, req.itemId(), req.quantity());
        return ResponseEntity.ok(invoker.process(command, session));
    }

    @PostMapping("/{code}/ready")
    public ResponseEntity<CommandSuccess<PartyResponse>> setReady(@PathVariable String code, @RequestBody SetReadyReqJSON req) {
        Session session = sessionValidator.currentSession();
        SetReadyCommand command = new SetReadyCommand(partyService, session, code, req.ready(), req.vote());
        return ResponseEntity.ok(invoker.process(command, session));
    }

    @PostMapping("/{code}/lock")
    public ResponseEntity<CommandSuccess<PartyResponse>> lock(@PathVariable String code) {
        Session session = sessionValidator.currentSession();
        return ResponseEntity.ok(invoker.process(new LockPartyCommand(partyService, session, code), session));
    }

    @PostMapping("/{code}/cancel")
    public ResponseEntity<CommandSuccess<PartyResponse>> cancel(@PathVariable String code) {
        Session session = sessionValidator.currentSession();
        return ResponseEntity.ok(invoker.process(new CancelPartyCommand(partyService, session, code), session));
    }

    @PostMapping("/{code}/checkout")
    public ResponseEntity<CommandSuccess<PartyCheckoutResponse>> checkout(@PathVariable String code) {
        Session session = sessionValidator.currentSession();
        return ResponseEntity.ok(invoker.process(new PartyCheckoutCommand(partyService, session, code), session));
    }

    @PostMapping("/{code}/pay")
    public ResponseEntity<CommandSuccess<PartyResponse>> pay(@PathVariable String code) {
        Session session = sessionValidator.currentSession();
        return ResponseEntity.ok(invoker.process(new PayPartyShareCommand(partyService, session, code), session));
    }
}
