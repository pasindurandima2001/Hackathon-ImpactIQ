package com.impactiq.controller;

import com.impactiq.dto.ProjectDtos.UserSummary;
import com.impactiq.security.CurrentUserService;
import com.impactiq.service.ProjectService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Registered accounts, for administrators assigning project members. */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final ProjectService projectService;
    private final CurrentUserService currentUser;

    public UserController(ProjectService projectService, CurrentUserService currentUser) {
        this.projectService = projectService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<UserSummary> list() {
        return projectService.users(currentUser.require());
    }
}
