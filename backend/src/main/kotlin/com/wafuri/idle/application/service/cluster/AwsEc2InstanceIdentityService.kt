package com.wafuri.idle.application.service.cluster

import com.wafuri.idle.application.config.ClusterConfig
import io.quarkus.arc.profile.IfBuildProfile
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@IfBuildProfile("prod")
@ApplicationScoped
class AwsEc2InstanceIdentityService(
  private val clusterConfig: ClusterConfig,
  @param:ConfigProperty(name = "quarkus.http.port") private val httpPort: Int,
) : InstanceIdentityService {
  private val httpClient: HttpClient =
    HttpClient
      .newBuilder()
      .connectTimeout(clusterConfig.aws().requestTimeout())
      .build()

  @Volatile
  private var cachedIdentity: InstanceIdentity? = null

  override fun current(): InstanceIdentity =
    cachedIdentity ?: synchronized(this) {
      cachedIdentity ?: resolveIdentity().also { cachedIdentity = it }
    }

  private fun resolveIdentity(): InstanceIdentity {
    val token = requestMetadataToken()
    val instanceId = requestMetadata("instance-id", token)
    val localIpv4 = requestMetadata("local-ipv4", token)
    return InstanceIdentity(
      instanceId = instanceId,
      internalBaseUrl = "http://$localIpv4:$httpPort",
    )
  }

  private fun requestMetadataToken(): String {
    val request =
      HttpRequest
        .newBuilder(metadataUri("/latest/api/token"))
        .timeout(clusterConfig.aws().requestTimeout())
        .header("X-aws-ec2-metadata-token-ttl-seconds", clusterConfig.aws().metadataTokenTtlSeconds().toString())
        .method("PUT", HttpRequest.BodyPublishers.noBody())
        .build()
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body().trim()
  }

  private fun requestMetadata(
    path: String,
    token: String,
  ): String {
    val request =
      HttpRequest
        .newBuilder(metadataUri("/latest/meta-data/$path"))
        .timeout(clusterConfig.aws().requestTimeout())
        .header("X-aws-ec2-metadata-token", token)
        .GET()
        .build()
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body().trim()
  }

  private fun metadataUri(path: String): URI = URI.create(clusterConfig.aws().metadataBaseUrl().trimEnd('/') + path)
}
