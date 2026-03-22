package com.wafuri.idle.persistence.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "cluster_nodes")
class ClusterNodeEntity {
  @Id
  lateinit var instanceId: String

  lateinit var internalBaseUrl: String

  lateinit var lastHeartbeatAt: Instant
}
