package com.fortunategoods.application.prc;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import com.fortunategoods.application.prc.history.HistoryResponse;
import com.fortunategoods.application.prc.summary.OrderSummaryResponse;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderEvent;
import com.fortunategoods.domain.order.OrderFinished;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.pricing.Fare;
import com.fortunategoods.domain.pricing.FareCalculator;
import com.fortunategoods.domain.pricing.LedgerEntry;
import com.fortunategoods.technical.persistence.OrderEventRepository;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.LedgerRepository;


// Writes one ledger entry when an order ends, and delegates role-scoped history (#264)
// and delivered-order summaries (#262) to OrderHistoryService.
// LedgerRepository (#236) stores the entries in ledger_entries.
@Service
public class LedgerService {
    private final LedgerRepository ledgerRepo;
    private final OrderRepository orderRepo;
    private final OrderEventRepository orderEventRepo;
    private final FareCalculator fareCalculator;
    private final OrderHistoryService orderHistoryService;

    public LedgerService(LedgerRepository ledgerRepo, OrderRepository orderRepo, OrderEventRepository orderEventRepo, FareCalculator fareCalculator,
            OrderHistoryService orderHistoryService) {
        this.ledgerRepo = ledgerRepo;
        this.orderRepo = orderRepo;
        this.orderEventRepo = orderEventRepo;
        this.fareCalculator = fareCalculator;
        this.orderHistoryService = orderHistoryService;
    }
    //https://www.baeldung.com/spring-events
    // Plain @EventListener: runs in the same transaction as the status change, so the entry and the change are saved together
    @EventListener
    public void onOrderFinished(OrderFinished event){
        if(event.getFinalStatus() == null){
            throw new IllegalStateException("OrderFinished has null end status");
        }

        // The order's events are oldest first: the first one is when it was placed, the last one is when it finished
        List<OrderEvent> events = orderEventRepo.findByOrderId(event.getOrderId());
        LocalDateTime placedAt = events.get(0).getTimestamp();
        LocalDateTime finishedAt = events.get(events.size() - 1).getTimestamp();

        if(event.getFinalStatus() == OrderStatus.DELIVERED){
            //get order to get order lines next
            Order order = orderRepo.findById(event.getOrderId());
            Fare fare = fareCalculator.calculate(order.getLines());
            String paymentReference = orderRepo.findPaymentId(event.getOrderId());
            ledgerRepo.save(LedgerEntry.charge(event.getOrderId(), fare, placedAt, finishedAt, paymentReference));
        }
        else if(event.getFinalStatus() == OrderStatus.CANCELLED || event.getFinalStatus() == OrderStatus.REJECTED ){
            ledgerRepo.save(LedgerEntry.noCharge(event.getOrderId(), event.getFinalStatus(), placedAt, finishedAt));
        }
    }

    // A new REFUND entry next to the order's CHARGE entry, which stays as it is.
    public void recordRefund(String orderId) {
        LedgerEntry charge = ledgerRepo.findCharge(orderId);
        if (charge == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "This order has no charge to refund");
        }
        List<OrderEvent> events = orderEventRepo.findByOrderId(orderId);
        ledgerRepo.save(LedgerEntry.refund(charge, events.get(events.size() - 1).getTimestamp()));
    }

    // Preserves the receiver interface used by GetHistoryCommand (#264, R-PRC-04).
    public List<HistoryResponse> historyOf(Session session) {
        return orderHistoryService.historyOf(session);
    }

    // Preserves the receiver interface used by GetOrderSummaryCommand (#262, R-PRC-02).
    public OrderSummaryResponse summaryOf(Session session, String orderId) {
        return orderHistoryService.summaryOf(session, orderId);
    }
}
