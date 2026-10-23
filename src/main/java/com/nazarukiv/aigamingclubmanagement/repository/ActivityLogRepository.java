package com.nazarukiv.aigamingclubmanagement.repository;

import com.nazarukiv.aigamingclubmanagement.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findByUserId(Long userId);
}
