package com.example.focusquest.blocking;

import com.example.focusquest.user.User;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockedTargetRepository extends RuleTargetRepository<BlockedTarget> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from BlockedTarget t where t.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
