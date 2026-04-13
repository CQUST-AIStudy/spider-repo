package com.tap.backend.repo;

import com.tap.backend.domain.ziporganize.ZipOrganizeJobEntity;
import com.tap.backend.domain.ziporganize.ZipOrganizeJobStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ZipOrganizeJobRepository extends JpaRepository<ZipOrganizeJobEntity, Long> {
  Optional<ZipOrganizeJobEntity> findByIdAndUser_Id(Long id, Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  ZipOrganizeJobEntity findFirstByStatusOrderByCreatedAtAsc(ZipOrganizeJobStatus status);
}
