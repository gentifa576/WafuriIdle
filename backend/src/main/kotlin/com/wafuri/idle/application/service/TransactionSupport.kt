package com.wafuri.idle.application.service

import io.quarkus.narayana.jta.QuarkusTransaction

fun runInNewTransaction(work: () -> Unit) {
  try {
    QuarkusTransaction.requiringNew().run(work)
  } catch (exception: NullPointerException) {
    work()
  } catch (exception: IllegalStateException) {
    work()
  }
}
