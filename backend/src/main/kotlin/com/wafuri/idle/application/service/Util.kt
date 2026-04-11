package com.wafuri.idle.application.service

import jakarta.enterprise.inject.Instance
import org.slf4j.Logger

fun <T> loadFirstNonEmpty(fetchers: Iterable<() -> List<T>>): List<T> =
  fetchers
    .asSequence()
    .map { it() }
    .firstOrNull { it.isNotEmpty() }
    .orEmpty()

fun <TBean, TValue> optionalFetcher(
  instance: Instance<TBean>,
  fetch: (TBean) -> List<TValue>,
): List<() -> List<TValue>> =
  if (instance.isResolvable) {
    listOf { fetch(instance.get()) }
  } else {
    emptyList()
  }

fun <T> refreshCatalogIfNotEmpty(
  fetch: () -> List<T>,
  replace: (Set<T>) -> Unit,
) {
  val content = fetch()
  if (content.isNotEmpty()) {
    replace(content.toSet())
  }
}

fun logLoadedContent(
  logger: Logger,
  content: Collection<*>,
  emptyMessage: String,
  countKey: String,
  loadedMessage: String,
) {
  if (content.isEmpty()) {
    logger.warn(emptyMessage)
    return
  }

  logger
    .atInfo()
    .addKeyValue(countKey, content.size)
    .log(loadedMessage)
}
