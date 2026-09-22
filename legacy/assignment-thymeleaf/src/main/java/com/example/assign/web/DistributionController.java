package com.example.assign.web;

import com.example.assign.AppClock;
import com.example.assign.dao.AssignmentDao;
import com.example.assign.dao.ClassDao;
import com.example.assign.model.AssignmentRow;
import com.example.assign.model.RedistributeResult;
import java.time.LocalDateTime;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.example.assign.service.DistributionService;

@Controller
public class DistributionController {

    private final DistributionService distributionService;
    private final AssignmentDao assignmentDao;
    private final ClassDao classDao;

    public DistributionController(DistributionService distributionService, AssignmentDao assignmentDao, ClassDao classDao) {
        this.distributionService = distributionService;
        this.assignmentDao = assignmentDao;
        this.classDao = classDao;
    }

    @GetMapping("/distributions")
    public String list(Model model) {
        model.addAttribute("rows", distributionService.listAll());
        model.addAttribute("now", AppClock.now());
        model.addAttribute("menu", "distributions");
        return "distributions";
    }

    @GetMapping("/distributions/new")
    public String form(Model model) {
        model.addAttribute("assignments", assignmentDao.findAllForSelect());
        model.addAttribute("classes", classDao.findAll());
        model.addAttribute("now", AppClock.now());
        model.addAttribute("menu", "new");
        return "distribution_form";
    }

    @PostMapping("/distributions")
    public String create(@RequestParam("assignmentId") long assignmentId,
                         @RequestParam("classId") long classId,
                         RedirectAttributes ra) {
        AssignmentRow a = assignmentDao.findById(assignmentId);
        if (a == null) {
            ra.addFlashAttribute("error", "존재하지 않는 과제입니다.");
            return "redirect:/distributions/new";
        }
        LocalDateTime now = AppClock.now();
        // 마감 지난 과제는 새 배포 불가. 연장(X) 상태만 예외
        if (a.getDueAt() != null && a.getDueAt().isBefore(now)) {
            if (!"X".equals(a.getStatus())) {
                ra.addFlashAttribute("error", "마감(" + AppClock.fmt(a.getDueAt()) + ")이 지난 과제는 배포할 수 없습니다. [" + a.getTitle() + "]");
                return "redirect:/distributions/new";
            }
        }
        try {
            long id = distributionService.distribute(assignmentId, classId);
            ra.addFlashAttribute("message", "배포가 등록되었습니다. (배포 #" + id + ")");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/distributions/new";
        } catch (DataAccessException e) {
            ra.addFlashAttribute("error", "DB 오류로 배포에 실패했습니다.");
            return "redirect:/distributions/new";
        }
        return "redirect:/distributions";
    }

    @PostMapping("/distributions/{id}/redistribute")
    public String redistribute(@PathVariable("id") long id,
                               @RequestParam(value = "reason", required = false) String reason,
                               @RequestParam(value = "operator", required = false) String operator,
                               RedirectAttributes ra) {
        RedistributeResult r;
        try {
            r = distributionService.redistribute(id, reason, operator);
        } catch (DataAccessException e) {
            ra.addFlashAttribute("error", "DB 오류로 재배포에 실패했습니다.");
            return "redirect:/distributions";
        }
        if (r.isOk()) {
            ra.addFlashAttribute("message", r.getMessage());
            if (!r.getWarnings().isEmpty()) {
                ra.addFlashAttribute("warnings", r.getWarnings());
            }
        } else {
            ra.addFlashAttribute("error", r.getMessage());
        }
        return "redirect:/distributions";
    }
}
