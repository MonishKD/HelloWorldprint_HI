package com.fortunategoods.application.dlv;

import org.springframework.stereotype.Service;

import com.fortunategoods.domain.courier.Assignment;
import com.fortunategoods.domain.courier.Courier;
import com.fortunategoods.domain.courier.CourierStatus;
import com.fortunategoods.domain.courier.DispatchQueue;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.technical.persistence.AssignmentRepository;
import com.fortunategoods.technical.persistence.CourierRepository;
import com.fortunategoods.technical.persistence.OrderRepository;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service 
public class CourierOfferService {

    private final CourierRepository couriers;
    private final AssignmentRepository assignments;
    private final OrderRepository orders;
    private final DispatchService dispatch;

    public CourierOfferService(CourierRepository couriers, AssignmentRepository assignments, OrderRepository orders, DispatchService dispatch)
    {
        this.couriers = couriers;
        this.assignments = assignments;
        this.orders = orders;
        this.dispatch = dispatch;
    }
    
    @Transactional
    public void accept(String courierId)
    {
        Assignment active = assignments.findActiveByCourier(courierId);
        if (active == null || active.getStatus() != Assignment.Status.OFFERED)
        {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "You have no pending offer");
        }
        orders.lockOrder(active.getOrderId());
        couriers.lockAll();
        Courier courier = couriers.findById(courierId);
        if (courier == null)
        {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "courier is null/not found!");
        }
        if (courier.getStatus() != CourierStatus.IDLE)
        {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "You cannot accept an order right now!");
        }
        if (!assignments.setStatus(Assignment.Status.ACCEPTED, Assignment.Status.OFFERED, active.getId()))
        {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "This offer is no longer available");
        }
        if (!couriers.updateStatusAndPosition(courierId, CourierStatus.IDLE, CourierStatus.ASSIGNED, null, null))
        {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Your status has changed, please try again!");
        }
        if (!orders.assignCourierIfUnchanged(active.getOrderId(), courierId))
        {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Order is no longer available!");
        }
    
    }

    @Transactional
    public void decline(String courierId)
    {
        Assignment active = assignments.findActiveByCourier(courierId);
        if (active == null || active.getStatus() != Assignment.Status.OFFERED)
        {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "You have no pending offer");
        }

        // Orders before couriers, like a cancel, or the two can deadlock. The offered order isn't in the waiting list yet.
        orders.lockOrder(active.getOrderId());
        orders.lockWaitingForDispatch();
        couriers.lockAll();

        if (!assignments.setStatus(Assignment.Status.DECLINED, Assignment.Status.OFFERED, active.getId())) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "This offer is no longer available");
        }
        
        List<DispatchQueue.WaitingOrder> waiting = orders.lockWaitingForDispatch();
        dispatch.retryDispatch(waiting);
    }
}
