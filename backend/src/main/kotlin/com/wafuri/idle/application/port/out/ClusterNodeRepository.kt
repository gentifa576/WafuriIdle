package com.wafuri.idle.application.port.out

import com.wafuri.idle.application.model.ClusterNode
import java.time.Instant

interface ClusterNodeRepository {
  fun save(node: ClusterNode): ClusterNode

  fun delete(instanceId: String)

  fun findAliveSince(cutoff: Instant): List<ClusterNode>
}
