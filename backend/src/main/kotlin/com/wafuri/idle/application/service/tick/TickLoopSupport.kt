package com.wafuri.idle.application.service.tick

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration

internal fun CoroutineScope.launchCorrectedLoop(
  intervalMillis: Long,
  block: suspend (Duration) -> Unit,
): Job =
  launch {
    var lastTickAtNanos = System.nanoTime()
    delay(intervalMillis)
    while (isActive) {
      val startedAtNanos = System.nanoTime()
      val elapsed = Duration.ofNanos((startedAtNanos - lastTickAtNanos).coerceAtLeast(0))
      lastTickAtNanos = startedAtNanos
      block(elapsed)
      val executionMillis = (System.nanoTime() - startedAtNanos) / 1_000_000
      val remainingDelay = (intervalMillis - executionMillis).coerceAtLeast(0)
      delay(remainingDelay)
    }
  }
