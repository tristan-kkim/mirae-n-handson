package com.example.assign.web;

import com.example.assign.AppClock;
import com.example.assign.dao.AssignmentDao;
import com.example.assign.model.AssignmentRow;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AssignmentController {

    private final AssignmentDao assignmentDao;

    public AssignmentController(AssignmentDao assignmentDao) {
        this.assignmentDao = assignmentDao;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/assignments";
    }

    @GetMapping("/assignments")
    public String list(Model model) {
        List<AssignmentRow> rows = assignmentDao.findAllForList();
        LocalDateTime now = AppClock.now();
        model.addAttribute("rows", rows);
        model.addAttribute("now", now);
        model.addAttribute("menu", "assignments");
        return "assignments";
    }
}
