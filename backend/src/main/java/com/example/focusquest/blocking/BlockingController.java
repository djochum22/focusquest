package com.example.focusquest.blocking;

import com.example.focusquest.user.User;
import com.example.focusquest.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class BlockingController {

    private final BlockingService blockingService;
    private final UserService userService;

    public BlockingController(BlockingService blockingService, UserService userService) {
        this.blockingService = blockingService;
        this.userService = userService;
    }

    @GetMapping("/api/blocked-targets")
    public List<RuleTargetResponse> listBlockedTargets(@AuthenticationPrincipal UserDetails principal) {
        return blockingService.listBlockedTargets(currentUser(principal)).stream()
                .map(RuleTargetResponse::from).toList();
    }

    @PostMapping("/api/blocked-targets")
    @ResponseStatus(HttpStatus.CREATED)
    public RuleTargetResponse createBlockedTarget(@AuthenticationPrincipal UserDetails principal,
                                                   @Valid @RequestBody RuleTargetRequest request) {
        return RuleTargetResponse.from(blockingService.createBlockedTarget(
                currentUser(principal), request.targetValue(), request.displayName(), request.active()));
    }

    @PutMapping("/api/blocked-targets/{id}")
    public RuleTargetResponse updateBlockedTarget(@AuthenticationPrincipal UserDetails principal,
                                                   @PathVariable("id") Long id,
                                                   @Valid @RequestBody RuleTargetRequest request) {
        return RuleTargetResponse.from(blockingService.updateBlockedTarget(
                currentUser(principal), id, request.targetValue(), request.displayName(), request.active()));
    }

    @DeleteMapping("/api/blocked-targets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBlockedTarget(@AuthenticationPrincipal UserDetails principal, @PathVariable("id") Long id) {
        blockingService.deleteBlockedTarget(currentUser(principal), id);
    }

    @GetMapping("/api/allowlist-targets")
    public List<RuleTargetResponse> listAllowlistTargets(@AuthenticationPrincipal UserDetails principal) {
        return blockingService.listAllowlistTargets(currentUser(principal)).stream()
                .map(RuleTargetResponse::from).toList();
    }

    @PostMapping("/api/allowlist-targets")
    @ResponseStatus(HttpStatus.CREATED)
    public RuleTargetResponse createAllowlistTarget(@AuthenticationPrincipal UserDetails principal,
                                                     @Valid @RequestBody RuleTargetRequest request) {
        return RuleTargetResponse.from(blockingService.createAllowlistTarget(
                currentUser(principal), request.targetValue(), request.displayName(), request.active()));
    }

    @PutMapping("/api/allowlist-targets/{id}")
    public RuleTargetResponse updateAllowlistTarget(@AuthenticationPrincipal UserDetails principal,
                                                     @PathVariable("id") Long id,
                                                     @Valid @RequestBody RuleTargetRequest request) {
        return RuleTargetResponse.from(blockingService.updateAllowlistTarget(
                currentUser(principal), id, request.targetValue(), request.displayName(), request.active()));
    }

    @DeleteMapping("/api/allowlist-targets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAllowlistTarget(@AuthenticationPrincipal UserDetails principal, @PathVariable("id") Long id) {
        blockingService.deleteAllowlistTarget(currentUser(principal), id);
    }

    private User currentUser(UserDetails principal) {
        return userService.getByUsername(principal.getUsername());
    }
}
