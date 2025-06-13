package io.stu.notify.repository;

import io.stu.notify.model.NotifyMessageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotifyMessageLogRepository
    extends JpaRepository<NotifyMessageLog, String>, JpaSpecificationExecutor<NotifyMessageLog> {

    long countByReceiverAndViewedFalse(String receiver);

    Page<NotifyMessageLog> findByReceiverOrderByViewedAscCreatedDesc(String receiver, Pageable page);

    Page<NotifyMessageLog> findByReceiverAndCreatedLessThanOrderByCreatedDesc(String receiver, long created, Pageable page);

    @Modifying
    @Query("UPDATE NotifyMessageLog n SET n.viewed = true WHERE n.id IN :ids AND n.receiver = :receiver")
    void updateViewedTrueByReceiverAndIdIn(String receiver, List<String> ids);

    void deleteByReceiver(String receiver);

    void deleteByReceiverAndIdIn(String receiver, List<String> ids);

}
