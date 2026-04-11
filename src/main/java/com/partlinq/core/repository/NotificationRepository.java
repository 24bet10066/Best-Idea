package com.partlinq.core.repository;

import com.partlinq.core.model.entity.Notification;
import com.partlinq.core.model.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	List<Notification> findByRecipientPhoneAndIsReadFalseOrderByCreatedAtDesc(String phone);

	List<Notification> findByIsSentFalseOrderByCreatedAtAsc();

	List<Notification> findByTechnicianIdAndNotificationTypeOrderByCreatedAtDesc(
		UUID technicianId, NotificationType type);

	List<Notification> findByShopIdOrderByCreatedAtDesc(UUID shopId);

	Long countByRecipientPhoneAndIsReadFalse(String phone);
}
