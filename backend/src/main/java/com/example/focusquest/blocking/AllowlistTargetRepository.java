package com.example.focusquest.blocking;

import com.example.focusquest.user.User;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AllowlistTargetRepository extends RuleTargetRepository<AllowlistTarget> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AllowlistTarget t where t.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
