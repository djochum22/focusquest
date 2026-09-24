package com.example.focusquest.export;

import com.example.focusquest.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DataDeletionController {

    private final DataDeletionService dataDeletionService;
    private final UserService userService;

    public DataDeletionController(DataDeletionService dataDeletionService, UserService userService) {
        this.dataDeletionService = dataDeletionService;
        this.userService = userService;
    }

    /** Deletes all of the caller's data and their account. The client is signed out afterwards. */
    @DeleteMapping("/api/me/data")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAllData(@AuthenticationPrincipal UserDetails principal) {
        dataDeletionService.deleteAllData(userService.getByUsername(principal.getUsername()));
    }
}
