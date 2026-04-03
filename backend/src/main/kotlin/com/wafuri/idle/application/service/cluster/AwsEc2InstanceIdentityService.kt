package com.wafuri.idle.application.service.cluster

import com.wafuri.idle.application.config.ClusterConfig
import io.quarkus.arc.profile.IfBuildProfile
import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.eclipse.microprofile.config.inject.ConfigProperty
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.imds.Ec2MetadataAsyncClient
import software.amazon.awssdk.imds.Ec2MetadataResponse
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@IfBuildProfile("prod")
@ApplicationScoped
class AwsEc2InstanceIdentityService(
  private val clusterConfig: ClusterConfig,
  @param:ConfigProperty(name = "quarkus.http.port") private val httpPort: Int,
) : InstanceIdentityService {
  private val metadataClient: Ec2MetadataAsyncClient =
    Ec2MetadataAsyncClient
      .builder()
      .endpoint(URI.create(clusterConfig.aws().metadataBaseUrl()))
      .tokenTtl(java.time.Duration.ofSeconds(clusterConfig.aws().metadataTokenTtlSeconds().toLong()))
      .httpClient(
        NettyNioAsyncHttpClient
          .builder()
          .connectionTimeout(clusterConfig.aws().requestTimeout())
          .readTimeout(clusterConfig.aws().requestTimeout())
          .writeTimeout(clusterConfig.aws().requestTimeout()),
      ).build()
  private val identityLock = Mutex()

  @Volatile
  private var cachedIdentity: InstanceIdentity? = null

  override suspend fun current(): InstanceIdentity =
    cachedIdentity ?: identityLock.withLock {
      cachedIdentity ?: resolveIdentity().also { cachedIdentity = it }
    }

  private suspend fun resolveIdentity(): InstanceIdentity {
    val instanceId = requestMetadata("instance-id")
    val localIpv4 = requestMetadata("local-ipv4")
    return InstanceIdentity(instanceId, "http://$localIpv4:$httpPort")
  }

  private suspend fun requestMetadata(path: String): String =
    metadataClient
      .get("/latest/meta-data/$path")
      .await()
      .asString()
      .trim()

  @PreDestroy
  fun stop() {
    metadataClient.close()
  }
}

private suspend fun CompletableFuture<Ec2MetadataResponse>.await(): Ec2MetadataResponse =
  suspendCancellableCoroutine { continuation ->
    whenComplete { result, exception ->
      if (exception == null) {
        continuation.resume(result)
      } else {
        continuation.resumeWithException((exception as? CompletionException)?.cause ?: exception)
      }
    }
  }
