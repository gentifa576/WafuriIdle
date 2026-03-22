package com.wafuri.idle.tests.support

import com.wafuri.idle.domain.model.CharacterTemplate
import com.wafuri.idle.domain.model.Item
import com.wafuri.idle.domain.model.ItemType
import com.wafuri.idle.domain.model.Stat
import com.wafuri.idle.domain.model.StatGrowth
import com.wafuri.idle.domain.model.StatType

fun warriorTemplate(key: String = "warrior"): CharacterTemplate =
  characterTemplate(
    key = key,
    name = "Warrior",
    strength = StatGrowth(12f, 2f),
    agility = StatGrowth(7f, 1.1f),
    intelligence = StatGrowth(4f, 0.4f),
    wisdom = StatGrowth(5f, 0.5f),
    vitality = StatGrowth(11f, 1.8f),
  )

fun clericTemplate(key: String = "cleric"): CharacterTemplate =
  characterTemplate(
    key = key,
    name = "Cleric",
    strength = StatGrowth(5f, 0.7f),
    agility = StatGrowth(4f, 0.6f),
    intelligence = StatGrowth(10f, 1.6f),
    wisdom = StatGrowth(12f, 1.9f),
    vitality = StatGrowth(8f, 1.2f),
  )

fun characterTemplate(
  key: String,
  name: String,
  strength: StatGrowth,
  agility: StatGrowth,
  intelligence: StatGrowth,
  wisdom: StatGrowth,
  vitality: StatGrowth,
): CharacterTemplate =
  CharacterTemplate(
    key = key,
    name = name,
    strength = strength,
    agility = agility,
    intelligence = intelligence,
    wisdom = wisdom,
    vitality = vitality,
  )

fun swordItem(): Item =
  Item(
    name = "Sword",
    displayName = "Sword",
    type = ItemType.WEAPON,
    baseStat = Stat(StatType.STRENGTH, 12f),
    subStatPool = listOf(StatType.AGILITY, StatType.VITALITY, StatType.CRIT_CHANCE),
  )

fun armorItem(): Item =
  Item(
    name = "Armor",
    displayName = "Armor",
    type = ItemType.ARMOR,
    baseStat = Stat(StatType.VITALITY, 18f),
    subStatPool = listOf(StatType.STRENGTH, StatType.WISDOM, StatType.CRIT_DAMAGE),
  )
