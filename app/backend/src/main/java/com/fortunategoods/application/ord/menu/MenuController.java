package com.fortunategoods.application.ord.menu;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.ord.orders.acceptance.AcceptOrderResponse;
import com.fortunategoods.application.ord.orders.rejection.RejectOrderResponse;
import com.fortunategoods.application.ord.orders.cancellation.CancelOrderCommand;
import com.fortunategoods.application.ord.orders.cancellation.CancelOrderResponse;
import com.fortunategoods.application.ord.orders.placement.PlaceOrderCommand;
import com.fortunategoods.application.ord.orders.placement.PlaceOrderReqJSON;
import com.fortunategoods.application.ord.orders.rejection.RejectOrderReqJSON;
import com.fortunategoods.application.ord.orders.placement.PlaceOrderResponse;
import com.fortunategoods.application.ord.orders.acceptance.AcceptOrderCommand;
import com.fortunategoods.application.ord.orders.rejection.RejectOrderCommand;
import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fortunategoods.application.reg.Session;
import com.fortunategoods.technical.security.SessionValidator;



    @RestController 
    @RequestMapping("api/restaurants")
    public class MenuController{
        private CommandInvoker invoker;
        private MenuService menuService;
        private SessionValidator sessionValidator;

        public MenuController(CommandInvoker invoker, MenuService menuService, SessionValidator sessionValidator){
            this.invoker = invoker;
            this.menuService = menuService;
            this.sessionValidator = sessionValidator;
        }
        //following OrderControll steps
        @PatchMapping("/{restaurantId}/menu/{itemId}/availability")
        public ResponseEntity<CommandSuccess<UpdateItemAvailabilityResponse>> updateItemAvailability(
            @PathVariable("restaurantId") String restaurantId,
            @PathVariable("itemId") String itemId,
            @RequestBody UpdateItemAvailabilityReqJSON req) {
                Session session = sessionValidator.currentSession();
                //create new command part for update availability
                UpdateItemAvailabilityCommand command = new UpdateItemAvailabilityCommand(menuService,session,restaurantId,itemId,req.toStatus());
                //get invoker to proccess new command
                CommandSuccess<UpdateItemAvailabilityResponse> result = invoker.process(command, session);

                //send result back to frontend
                return ResponseEntity.ok(result);
            }

    }
