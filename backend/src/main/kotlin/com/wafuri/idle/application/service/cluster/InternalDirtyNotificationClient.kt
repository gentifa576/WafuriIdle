package com.wafuri.idle.application.service.cluster

import com.wafuri.idle.application.config.ClusterConfig
import com.wafuri.idle.application.service.auth.JwtTokenService
import io.smallrye.mutiny.coroutines.awaitSuspending
import io.vertx.core.http.HttpMethod
import io.vertx.mutiny.core.Vertx
import io.vertx.mutiny.core.buffer.Buffer
import io.vertx.mutiny.ext.web.client.HttpResponse
import io.vertx.mutiny.ext.web.client.WebClient
import jakarta.enterprise.context.ApplicationScoped
import java.net.URI
import java.util.UUID

@ApplicationScoped
class InternalDirtyNotificationClient(
  private val clusterConfig: ClusterConfig,
  private val jwtTokenService: JwtTokenService,
  private val clusterNodeHeartbeatService: ClusterNodeHeartbeatService,
  vertx: Vertx,
) {
  private val webClient: WebClient = WebClient.create(vertx)

  suspend fun notifyDirty(
    targetBaseUrl: String,
    playerId: UUID,
  ) {
    val token = jwtTokenService.mintInternalNode(clusterNodeHeartbeatService.currentIdentity().instanceId)
    val requestUri = URI.create(targetBaseUrl.trimEnd('/') + "/internal/players/$playerId/dirty")
    val response: HttpResponse<Buffer> =
      webClient
        .requestAbs(HttpMethod.POST, requestUri.toString())
        .timeout(clusterConfig.notify().requestTimeout().toMillis())
        .putHeader("Authorization", "Bearer $token")
        .send()
        .awaitSuspending()
    require(response.statusCode() in 200..299) {
      "Dirty player notification failed with status ${response.statusCode()}."
    }
  }
}
