package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.service.ConfigReleaseService.RawConfig;
import com.example.payments.platform.service.service.ConfigReleaseService.ReleaseResponse;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConfigReleaseMapper {
  long countAll();

  List<ReleaseResponse> selectPage(@Param("limit") int limit, @Param("offset") int offset);

  long nextVersionForUpdate();

  int insert(
      @Param("releaseId") String releaseId,
      @Param("version") long version,
      @Param("config") String config,
      @Param("createdBy") String createdBy,
      @Param("createdAt") Instant createdAt);

  int publish(@Param("releaseId") String releaseId, @Param("publishedAt") Instant publishedAt);

  int disableOtherPublished(@Param("releaseId") String releaseId);

  String selectPreviousConfig(@Param("version") long version);

  int transition(
      @Param("releaseId") String releaseId,
      @Param("from") String from,
      @Param("to") String to,
      @Param("approver") String approver);

  ReleaseResponse selectById(@Param("releaseId") String releaseId);

  RawConfig selectRawById(@Param("releaseId") String releaseId);
}
