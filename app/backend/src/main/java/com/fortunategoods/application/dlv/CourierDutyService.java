package com.fortunategoods.application.dlv;

import com.fortunategoods.technical.persistence.AssignmentRepository;
import com.fortunategoods.technical.persistence.CourierRepository;
import com.fortunategoods.technical.persistence.OrderRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;

import com.fortunategoods.domain.courier.Courier;
import com.fortunategoods.domain.courier.CourierDashboardView;
import com.fortunategoods.domain.courier.CourierJob;
import com.fortunategoods.domain.courier.CourierStatus;
import com.fortunategoods.domain.courier.DispatchQueue;
import com.fortunategoods.domain.courier.DlvModulePermissions;
import com.fortunategoods.domain.courier.ReadyOrderSummary;
import com.fortunategoods.domain.order.DomainRuleViolation;
import com.fortunategoods.domain.order.OrdModulePermissions;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.domain.courier.Assignment;

import java.util.List;

@Service
public class CourierDutyService {
    private final OrderRepository orders;
    private final CourierRepository couriers;
    private final AssignmentRepository assignments;
    private final DispatchService dispatch;

    public CourierDutyService(OrderRepository orders, CourierRepository couriers, AssignmentRepository assignments, DispatchService dispatch) {
        this.orders = orders;
        this.couriers = couriers;
        this.assignments = assignments;
        this.dispatch = dispatch;
    }

    @Transactional   // outermost method, so it opens the transaction
    public void goOnDuty(String courierId) {
        // we need to lock the waiting orders first. also key to keep the list for retryDispatch()!
        List<DispatchQueue.WaitingOrder> waitingOrders = orders.lockWaitingForDispatch();
        couriers.lockAll(); 
        Courier courier = couriers.findById(courierId); 
        if (courier == null) {
        throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Courier not found");
        }
        CourierStatus courierStatus = courier.getStatus();
        if (courierStatus != CourierStatus.OFFLINE) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Courier is already on duty!");
        }
        courier.goOnDuty();
        if (!couriers.updateStatusAndPosition(courierId, courierStatus, courier.getStatus(), null, null)) {
        throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Your status changed, please try again");
        }
        dispatch.retryDispatch(waitingOrders);
    }

    @Transactional   // outermost method, so it opens the transaction
    public void goOffDuty(String courierId) {
        List<DispatchQueue.WaitingOrder> waitingOrders = orders.lockWaitingForDispatch();
        couriers.lockAll(); // lock all couriers to prevent race conditions
        Courier courier = couriers.findById(courierId); 
        if (courier == null) {
        throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Courier not found");
        }
        CourierStatus previousStatus = courier.getStatus();
        if (previousStatus == CourierStatus.OFFLINE) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Courier is already off duty!");
        }

        courier.goOffDuty();
        if (!couriers.updateStatusAndPosition(courierId, previousStatus, courier.getStatus(), null, null)) {
        throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Your status changed, please try again");
        }

        Assignment active = assignments.findActiveByCourier(courierId);
        if (active != null && active.getStatus() == Assignment.Status.OFFERED) {
            if (!assignments.setStatus(Assignment.Status.WITHDRAWN, Assignment.Status.OFFERED, active.getId())) {
                throw new DomainRuleViolation(DomainRuleViolation.Reason.CONFLICT, "Your offer changed, please try again");
            }
        }
        dispatch.retryDispatch(waitingOrders);
    }

    public List<ReadyOrderSummary> readyOrders(UserRole role) {
       OrdModulePermissions.requireRole(role, DlvModulePermissions.viewReadyOrdersRoles(), "Only couriers can see the ready orders");
       return orders.findReadyOrdersForCourier();
   }

   // The signed-in courier's own state, current offer and current order. Read only.
    @Transactional(readOnly = true)
    public CourierDashboardView courierDashboard(UserRole role, String courierId) {
        OrdModulePermissions.requireRole(role, DlvModulePermissions.viewOwnCourierStateRoles(), "Only couriers can see their own dashboard");

        Courier courier = couriers.findById(courierId);
        if (courier == null) {
            throw new DomainRuleViolation(DomainRuleViolation.Reason.NOT_FOUND, "Courier not found");
        }

        Assignment active = assignments.findActiveByCourier(courierId);

        CourierJob offer = null;
        CourierJob currentOrder = null;

        if (active != null) {
            CourierJob job = orders.findCourierJob(active.getOrderId());
            if (active.getStatus() == Assignment.Status.OFFERED) {
                offer = job;
            } else if (active.getStatus() == Assignment.Status.ACCEPTED) {
                currentOrder = job;
            }
        }
        
        return new CourierDashboardView(courier.getStatus(), offer, currentOrder);
    }

}