package com.fortunategoods.application.dlv;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandSuccess;
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

    public CourierController(CourierDutyService givenDutyService, SessionValidator givenSessionValidator, CommandInvoker givenInvoker) {
        this.dutyService = givenDutyService;
        this.sessionValidator = givenSessionValidator;
        this.invoker = givenInvoker;
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
    
}
