package com.example.payments.platform.service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ConfigurationSnapshotServiceTest {
  private final com.example.payments.platform.service.mapper.ConfigurationSnapshotMapper mapper =
      mock(com.example.payments.platform.service.mapper.ConfigurationSnapshotMapper.class);

  @Test
  void resolvesOnlyAnActiveAppIdBelongingToTheRequestedMerchant() {
    when(mapper.selectActiveProductCodeByAppId("merchant-a", 1001L)).thenReturn("CARD-US-USD");

    assertEquals("CARD-US-USD", new ConfigurationSnapshotService(mapper).productCodeByAppId("merchant-a", 1001));
  }

  @Test
  void rejectsInvalidOrUnavailableAppIds() {
    var service = new ConfigurationSnapshotService(mapper);

    assertThrows(ResponseStatusException.class, () -> service.productCodeByAppId("merchant-a", 999));
    when(mapper.selectActiveProductCodeByAppId("merchant-a", 1002L)).thenReturn(null);
    assertThrows(ResponseStatusException.class, () -> service.productCodeByAppId("merchant-a", 1002));
  }

  @Test
  void selectsByWeightInsideTheHighestApplicableTier() {
    var candidates =
        List.of(
            new ConfigurationSnapshotService.ChannelCandidate("channel-a", 0, 10, 70),
            new ConfigurationSnapshotService.ChannelCandidate("channel-b", 0, 10, 30),
            new ConfigurationSnapshotService.ChannelCandidate("channel-c", 0, 20, 100),
            new ConfigurationSnapshotService.ChannelCandidate("channel-d", 1, 1, 100));

    assertEquals(
        "channel-a",
        ConfigurationSnapshotService.selectWeightedCandidate(candidates, 69).channelId());
    assertEquals(
        "channel-b",
        ConfigurationSnapshotService.selectWeightedCandidate(candidates, 70).channelId());
  }
}
