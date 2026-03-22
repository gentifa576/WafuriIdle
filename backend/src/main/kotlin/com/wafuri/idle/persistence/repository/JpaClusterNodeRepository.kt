package com.wafuri.idle.persistence.repository

import com.wafuri.idle.application.model.ClusterNode
import com.wafuri.idle.application.port.out.ClusterNodeRepository
import com.wafuri.idle.persistence.entity.ClusterNodeEntity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import java.time.Instant

@ApplicationScoped
class JpaClusterNodeRepository(
  private val entityManager: EntityManager,
) : ClusterNodeRepository {
  override fun save(node: ClusterNode): ClusterNode {
    val entity =
      (entityManager.find(ClusterNodeEntity::class.java, node.instanceId) ?: ClusterNodeEntity()).also {
        it.instanceId = node.instanceId
        it.internalBaseUrl = node.internalBaseUrl
        it.lastHeartbeatAt = node.lastHeartbeatAt
      }
    return entityManager.merge(entity).toDomain()
  }

  override fun delete(instanceId: String) {
    entityManager.find(ClusterNodeEntity::class.java, instanceId)?.let(entityManager::remove)
  }

  override fun findAliveSince(cutoff: Instant): List<ClusterNode> =
    entityManager
      .createQuery(
        "from ClusterNodeEntity where lastHeartbeatAt >= :cutoff order by instanceId",
        ClusterNodeEntity::class.java,
      ).setParameter("cutoff", cutoff)
      .resultList
      .map(ClusterNodeEntity::toDomain)
}

private fun ClusterNodeEntity.toDomain(): ClusterNode =
  ClusterNode(
    instanceId = instanceId,
    internalBaseUrl = internalBaseUrl,
    lastHeartbeatAt = lastHeartbeatAt,
  )
