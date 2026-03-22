package com.wafuri.idle.application.service.cluster

import com.wafuri.idle.application.model.ClusterNode
import com.wafuri.idle.application.port.out.ClusterNodeRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Instant

@ApplicationScoped
class ClusterNodeHeartbeatService(
  private val clusterNodeRepository: ClusterNodeRepository,
  private val instanceIdentityService: InstanceIdentityService,
) {
  fun currentIdentity(): InstanceIdentity = instanceIdentityService.current()

  @Transactional
  fun heartbeat(at: Instant = Instant.now()) {
    val identity = instanceIdentityService.current()
    clusterNodeRepository.save(
      ClusterNode(
        instanceId = identity.instanceId,
        internalBaseUrl = identity.internalBaseUrl,
        lastHeartbeatAt = at,
      ),
    )
  }

  @Transactional
  fun removeCurrentNode() {
    clusterNodeRepository.delete(instanceIdentityService.current().instanceId)
  }
}
