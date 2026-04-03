package com.wafuri.idle.application.service.cluster

import com.wafuri.idle.application.config.ClusterConfig
import io.quarkus.arc.DefaultBean
import jakarta.enterprise.context.ApplicationScoped

@DefaultBean
@ApplicationScoped
class ConfiguredInstanceIdentityService(
  private val clusterConfig: ClusterConfig,
) : InstanceIdentityService {
  override suspend fun current(): InstanceIdentity =
    InstanceIdentity(
      clusterConfig.local().instanceId(),
      clusterConfig.local().internalBaseUrl(),
    )
}
