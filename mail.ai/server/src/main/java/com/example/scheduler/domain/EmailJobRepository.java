package com.example.scheduler.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailJobRepository extends JpaRepository<EmailJob, UUID> {
	List<EmailJob> findByScheduleIdOrderByDueAtAsc(UUID scheduleId);

	@Query("""
			SELECT job
			FROM EmailJob job
			JOIN FETCH job.schedule
			WHERE job.id = :id
			""")
	Optional<EmailJob> findByIdWithSchedule(@Param("id") UUID id);

	@Modifying
	@Transactional
	@Query("""
			UPDATE EmailJob job
			SET job.state = com.example.scheduler.domain.JobState.SENDING
			WHERE job.id = :id
			  AND job.state = com.example.scheduler.domain.JobState.PENDING
			  AND job.dueAt <= CURRENT_TIMESTAMP
			  AND job.schedule.state = com.example.scheduler.domain.ScheduleState.ACTIVE
			""")
	int claimDueJob(@Param("id") UUID id);

	@Query("""
			SELECT job
			FROM EmailJob job
			JOIN FETCH job.schedule
			WHERE job.schedule.ownerId = :ownerId
			ORDER BY job.dueAt ASC
			""")
	List<EmailJob> findByOwnerIdOrderByDueAtAsc(@Param("ownerId") String ownerId);
}
