package com.wafuri.idle.application.service.cluster

data class InstanceIdentity(
  val instanceId: String,
  val internalBaseUrl: String,
)

interface InstanceIdentityService {
  fun current(): InstanceIdentity
}
