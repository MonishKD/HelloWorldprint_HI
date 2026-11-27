package com.fortunategoods.application.rvw;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.application.ord.execution.CommandSuccess;
import com.fortunategoods.application.reg.Session;
import com.fortunategoods.domain.rating.RatingScale;
import com.fortunategoods.technical.security.SessionValidator;

/*
    REST endpoints for ratings (#324):
      GET  /api/ratings/scale             -> { minStars, maxStars }, for any signed-in user
      POST /api/orders/{orderId}/rating   -> a customer rates their delivered order
    @RestController: Spring sends every request that matches the paths below to this class,
    and turns what the method returns into JSON.
*/
@RestController
public class RatingController {

    private final CommandInvoker invoker;
    private final RatingService ratingService;
    private final SessionValidator sessionValidator; // gives the session of the signed-in user

    public RatingController(CommandInvoker invoker, RatingService ratingService, SessionValidator sessionValidator) {
        this.invoker = invoker;
        this.ratingService = ratingService;
        this.sessionValidator = sessionValidator;
    }

    // Reading the scale is not a command, so it is not logged (like the console feed). currentSession() refuses a user who is not signed in.
    @GetMapping("/api/ratings/scale")
    public ResponseEntity<CommandSuccess<RatingScale>> getScale() {
        sessionValidator.currentSession();
        return ResponseEntity.ok(CommandSuccess.success(ratingService.scale(), "Rating scale loaded"));
    }

    @PostMapping("/api/orders/{orderId}/rating")
    public ResponseEntity<CommandSuccess<RatingResponse>> rateOrder(@PathVariable String orderId,
            @RequestBody RateOrderReqJSON req) {
        Session session = sessionValidator.currentSession();
        RateOrderCommand command = new RateOrderCommand(ratingService, session, orderId, req.restaurantStars(), req.courierStars());
        return ResponseEntity.ok(invoker.process(command, session));
    }
}
