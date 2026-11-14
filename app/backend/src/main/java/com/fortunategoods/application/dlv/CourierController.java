package com.fortunategoods.application.dlv;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.domain.courier.CourierDashboardView;
import com.fortunategoods.domain.courier.CourierStatus;
import com.fortunategoods.technical.security.SessionValidator;
import com.fortunategoods.application.reg.Session;

import com.fortunategoods.domain.courier.ReadyOrderSummary;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@RestController
public class CourierController {
    private final CourierDutyService dutyService;
    private final SessionValidator sessionValidator;
    private final CommandInvoker invoker;
    private final CourierOfferService offerService;

    public CourierController(CourierDutyService givenDutyService, SessionValidator givenSessionValidator, CommandInvoker givenInvoker, CourierOfferService offerService) {
        this.dutyService = givenDutyService;
        this.sessionValidator = givenSessionValidator;
        this.invoker = givenInvoker;
        this.offerService = offerService;
    }

    @PostMapping("/api/couriers/me/on-duty") 
    public ResponseEntity<CommandSuccess<CourierStatus>> goOnDuty()
    {
        Session session = sessionValidator.currentSession();
        return ResponseEntity.ok(invoker.process(new GoOnDutyCommand(dutyService, session), session));
    }

    @PostMapping("/api/couriers/me/off-duty")
    public ResponseEntity<CommandSuccess<CourierStatus>> goOffDuty()
    {
        Session session = sessionValidator.currentSession();
        return ResponseEntity.ok(invoker.process(new GoOffDutyCommand(dutyService, session), session));

    }

    @GetMapping("/api/couriers/ready-orders")
    public ResponseEntity<CommandSuccess<List<ReadyOrderSummary>>> getReadyOrders()
    {
        Session session = sessionValidator.currentSession();
        return ResponseEntity.ok(invoker.process(new GetReadyOrdersCommand(dutyService, session), session));
    }

    // Couriers only (403 otherwise). The signed-in courier's own state.
    @GetMapping("/api/couriers/me")
    public ResponseEntity<CommandSuccess<CourierDashboardView>> getCourierDashboard() {
        Session session = sessionValidator.currentSession();
        return ResponseEntity.ok(invoker.process(new GetCourierDashboardCommand(dutyService, session), session));
    }

    // Couriers only (403 otherwise).
    @PostMapping("/api/couriers/me/offer/accept")
    public ResponseEntity<CommandSuccess<CourierStatus>> acceptOffer() {
        Session session = sessionValidator.currentSession();
        return ResponseEntity.ok(invoker.process(new AcceptOfferCommand(offerService, session), session));
    }

    // Couriers only (403 otherwise). The order goes back to the dispatch queue.
    @PostMapping("/api/couriers/me/offer/decline")
    public ResponseEntity<CommandSuccess<CourierStatus>> declineOffer() {
        Session session = sessionValidator.currentSession();
        return ResponseEntity.ok(invoker.process(new DeclineOfferCommand(offerService, session), session));
    }
    
}
