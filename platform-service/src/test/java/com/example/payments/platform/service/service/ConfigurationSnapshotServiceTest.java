package com.example.payments.platform.service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigurationSnapshotServiceTest {
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
