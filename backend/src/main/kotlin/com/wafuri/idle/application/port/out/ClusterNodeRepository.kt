package com.wafuri.idle.application.port.out

import com.wafuri.idle.application.model.ClusterNode
import java.time.Instant

interface ClusterNodeRepository {
  suspend fun save(node: ClusterNode): ClusterNode

  suspend fun delete(instanceId: String)

  suspend fun findAliveSince(cutoff: Instant): List<ClusterNode>
}
