package com.example.assign.web;

import org.springframework.dao.DataAccessException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class DbErrorAdvice {

    @ExceptionHandler(DataAccessException.class)
    public String dbError(DataAccessException e, Model model) {
        System.out.println("[DB-ERROR] " + e.getClass().getSimpleName() + " : " + e.getMostSpecificCause().getMessage());
        model.addAttribute("menu", "");
        model.addAttribute("detail", e.getMostSpecificCause().getMessage());
        return "db_error";
    }
}
