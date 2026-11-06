package com.fortunategoods.application.dlv;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import com.fortunategoods.application.dlv.reassignment.GetStalledOrdersCommand;
import com.fortunategoods.application.dlv.reassignment.ReassignOrderCommand;
import com.fortunategoods.application.dlv.reassignment.ReassignOrderResponse;
import com.fortunategoods.application.dlv.reassignment.StalledOrderResponse;
import com.fortunategoods.application.dlv.handoff.ConfirmDeliveryCommand;
import com.fortunategoods.application.dlv.handoff.ConfirmPickupCommand;
import com.fortunategoods.application.dlv.handoff.HandoffResponse;
import com.fortunategoods.application.dlv.queue.DispatchQueueEntry;
import com.fortunategoods.application.dlv.queue.GetDispatchQueueCommand;
import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.technical.security.SessionValidator;

@RestController
public class DlvController {

    private final DispatchService dispatchService;
    private final DeliveryService deliveryService;
    private final ReassignmentService reassignmentService;
    private final SessionValidator sessionValidator;
    private final CommandInvoker invoker;

    public DlvController(DispatchService dispatchService, DeliveryService deliveryService, ReassignmentService reassignmentService, SessionValidator sessionValidator, CommandInvoker invoker) {
        this.dispatchService = dispatchService;
        this.deliveryService = deliveryService;
        this.reassignmentService = reassignmentService;
        this.sessionValidator = sessionValidator;
        this.invoker = invoker;
    }

    // Operators only (403 otherwise).
    @GetMapping("/api/operator/dispatch-queue")
    public ResponseEntity<CommandSuccess<List<DispatchQueueEntry>>> getDispatchQueue() {
        Session session = sessionValidator.currentSession();
        return ResponseEntity.ok(invoker.process(new GetDispatchQueueCommand(dispatchService, session), session));
    }

    // Confirm pick up endpoint (couriers only or else 403)
    @PostMapping("/api/couriers/me/order/pickup")
    public ResponseEntity<CommandSuccess<HandoffResponse>> confirmPickup() {
        Session session = sessionValidator.currentSession();
        return ResponseEntity.ok(invoker.process(new ConfirmPickupCommand(deliveryService, session), session));
    }

    // Confirm delivery endpoint (couriers only or else 403)
    @PostMapping("/api/couriers/me/order/deliver")
    public ResponseEntity<CommandSuccess<HandoffResponse>> confirmDelivery() {
        Session session = sessionValidator.currentSession();
        return ResponseEntity.ok(invoker.process(new ConfirmDeliveryCommand(deliveryService, session), session));
    }

    // Get stalled orders (operator only or else 403)
    @GetMapping("/api/operator/stalled-orders")
    public ResponseEntity<CommandSuccess<List<StalledOrderResponse>>> getStalledOrders() {
        Session session = sessionValidator.currentSession();
        return ResponseEntity.ok(invoker.process(new GetStalledOrdersCommand(reassignmentService, session), session));
    }

    // Reassign orders orders (operator only or else 403)
    @PostMapping("/api/orders/{orderId}/reassign")
    public ResponseEntity<CommandSuccess<ReassignOrderResponse>> reassignOrder(@PathVariable("orderId") String orderId) {
        Session session = sessionValidator.currentSession();
        return ResponseEntity.ok(invoker.process(new ReassignOrderCommand(reassignmentService, session, orderId), session));
    }
}
