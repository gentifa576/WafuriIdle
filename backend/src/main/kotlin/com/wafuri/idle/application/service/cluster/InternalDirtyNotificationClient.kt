package com.wafuri.idle.application.service.cluster

import com.wafuri.idle.application.config.ClusterConfig
import com.wafuri.idle.application.service.auth.JwtTokenService
import jakarta.enterprise.context.ApplicationScoped
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

@ApplicationScoped
class InternalDirtyNotificationClient(
  private val clusterConfig: ClusterConfig,
  private val jwtTokenService: JwtTokenService,
  private val clusterNodeHeartbeatService: ClusterNodeHeartbeatService,
) {
  private val httpClient: HttpClient =
    HttpClient
      .newBuilder()
      .connectTimeout(clusterConfig.notify().requestTimeout())
      .build()

  fun notifyDirty(
    targetBaseUrl: String,
    playerId: UUID,
  ): Boolean {
    val token = jwtTokenService.mintInternalNode(clusterNodeHeartbeatService.currentIdentity().instanceId)
    val request =
      HttpRequest
        .newBuilder(URI.create(targetBaseUrl.trimEnd('/') + "/internal/players/$playerId/dirty"))
        .timeout(clusterConfig.notify().requestTimeout())
        .header("Authorization", "Bearer $token")
        .POST(HttpRequest.BodyPublishers.noBody())
        .build()
    val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
    return response.statusCode() in 200..299
  }
}
