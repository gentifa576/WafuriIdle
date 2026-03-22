package com.wafuri.idle.application.port.out

interface Repository<T, ID> {
  fun save(domain: T): T

  fun findById(id: ID): T?
}
