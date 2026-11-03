package com.fortunategoods.application.prc;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderFinished;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.order.OrderStatus;
import com.fortunategoods.domain.pricing.FareCalculator;
import com.fortunategoods.domain.pricing.LedgerEntry;
import com.fortunategoods.technical.persistence.OrderRepository;
import com.fortunategoods.technical.persistence.LedgerRepository;


//Ledger Repo not implemented yet but following previous repos logic
@Service 
public class LedgerService {
    private final LedgerRepository ledgerRepo;
    private final OrderRepository orderRepo;
    private final FareCalculator fareCalculator;

    public LedgerService(LedgerRepository ledgerRepo, OrderRepository orderRepo, FareCalculator fareCalculator) {
        this.ledgerRepo = ledgerRepo;
        this.orderRepo = orderRepo;
        this.fareCalculator = fareCalculator;
    }
    //https://www.baeldung.com/spring-events
    @EventListener 
    public void onOrderFinished(OrderFinished event){
        if(event.getFinalStatus() == null){
            throw new IllegalStateException("OrderFinished has null end status");
        }
        else if(event.getFinalStatus() == OrderStatus.DELIVERED){
            //get order to get order lines next
            Order order = orderRepo.findById(event.getOrderId());
            List<OrderLine> orderLine = order.getLines();
            long total = fareCalculator.calculate(orderLine).getTotalCents();
            ledgerRepo.save(LedgerEntry.charge(event.getOrderId(), total));
        }
        else if(event.getFinalStatus() == OrderStatus.CANCELLED || event.getFinalStatus() == OrderStatus.REJECTED ){
            ledgerRepo.save(LedgerEntry.noCharge(event.getOrderId()));
        }
    }
}
