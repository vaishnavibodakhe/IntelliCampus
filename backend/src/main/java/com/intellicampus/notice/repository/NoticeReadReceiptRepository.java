package com.intellicampus.notice.repository;

import com.intellicampus.notice.model.Notice;
import com.intellicampus.notice.model.NoticeReadReceipt;
import com.intellicampus.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NoticeReadReceiptRepository extends JpaRepository<NoticeReadReceipt, Long> {
    Optional<NoticeReadReceipt> findByNoticeAndUser(Notice notice, User user);
    boolean existsByNoticeAndUser(Notice notice, User user);
    long countByNotice(Notice notice);
    List<NoticeReadReceipt> findByUser(User user);
}
