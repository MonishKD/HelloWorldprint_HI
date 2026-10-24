package com.fortunategoods.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import com.fortunategoods.application.ord.OrderExceptionHandler;
import com.fortunategoods.application.ord.execution.CommandInvoker;
import com.fortunategoods.technical.security.SecurityConfig;
import com.fortunategoods.technical.security.SecurityErrorResponses;
import com.fortunategoods.technical.security.role.RoleCheck;
import com.fortunategoods.technical.security.session.SpringSecuritySessionValidator;

/*
    A @WebMvcTest of one controller with the real security, session check, CommandInvoker and error responses.
    Usage: @SecuredControllerTest(MenuController.class). The test still declares its own @MockitoBean repositories.
*/
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebMvcTest(properties = {
    "app.cors.allowed-origin=http://localhost:5173",
    "spring.application.name=fortunategoods-test"
})
@Import({
    SecurityConfig.class, SecurityErrorResponses.class,
    SpringSecuritySessionValidator.class, RoleCheck.class,
    CommandInvoker.class, OrderExceptionHandler.class
})
public @interface SecuredControllerTest {

    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value() default {};
}
