package com.aiengineering.web.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aiengineering.security.SecurityUtils;
import com.aiengineering.security.UserPrincipal;
import com.aiengineering.service.UserService;
import com.aiengineering.web.dto.user.UserResponse;
import com.aiengineering.web.dto.user.UserSummaryResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @GetMapping("/me")
    UserResponse me() {
        logger.debug("me: resolving current user");
        UserPrincipal principal = SecurityUtils.requireCurrentUser();
        logger.info("Principal: {}", principal);
        return userService.getById(principal.id());
    }

    @GetMapping("/search")
    List<UserSummaryResponse> search(@RequestParam String q) {
        logger.debug("search: q={}", q);
        SecurityUtils.requireCurrentUser();
        return userService.searchSummaries(q);
    }
}
