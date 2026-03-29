package com.wafuri.idle.tests.support

import com.wafuri.idle.domain.model.CharacterTemplate
import com.wafuri.idle.domain.model.CombatConditionDefinition
import com.wafuri.idle.domain.model.CombatConditionType
import com.wafuri.idle.domain.model.CombatEffectDefinition
import com.wafuri.idle.domain.model.CombatEffectType
import com.wafuri.idle.domain.model.CombatModifierDefinition
import com.wafuri.idle.domain.model.CombatModifierType
import com.wafuri.idle.domain.model.CombatStatType
import com.wafuri.idle.domain.model.CombatTargetType
import com.wafuri.idle.domain.model.CombatValueFormula
import com.wafuri.idle.domain.model.CombatValueFormulaType
import com.wafuri.idle.domain.model.Item
import com.wafuri.idle.domain.model.ItemType
import com.wafuri.idle.domain.model.PassiveDefinition
import com.wafuri.idle.domain.model.PassiveTriggerType
import com.wafuri.idle.domain.model.SkillDefinition
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
    tags = listOf("male", "frontline"),
    skill =
      SkillDefinition(
        key = "shield_wall",
        name = "Shield Wall",
        cooldownMillis = 12_000,
        effects =
          listOf(
            CombatEffectDefinition(
              type = CombatEffectType.APPLY_DAMAGE_REDIRECT,
              target = CombatTargetType.SELF,
              durationMillis = 5_000,
            ),
            CombatEffectDefinition(
              type = CombatEffectType.APPLY_EFFECT,
              target = CombatTargetType.SELF,
              durationMillis = 5_000,
              modifiers =
                listOf(
                  CombatModifierDefinition(
                    type = CombatModifierType.INCOMING_DAMAGE_MULTIPLIER,
                    value = -0.3f,
                  ),
                ),
            ),
          ),
      ),
    passive =
      PassiveDefinition(
        key = "battle_standard",
        name = "Battle Standard",
        trigger = PassiveTriggerType.AURA,
        condition = CombatConditionDefinition(type = CombatConditionType.ALWAYS),
        modifiers =
          listOf(
            CombatModifierDefinition(
              type = CombatModifierType.ATTACK_MULTIPLIER,
              value = 0.1f,
            ),
          ),
      ),
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
    tags = listOf("female", "support"),
    skill =
      SkillDefinition(
        key = "renewing_prayer",
        name = "Renewing Prayer",
        cooldownMillis = 6_000,
        effects =
          listOf(
            CombatEffectDefinition(
              type = CombatEffectType.HEAL,
              target = CombatTargetType.LOWEST_HP_ALLY,
              amount =
                CombatValueFormula(
                  type = CombatValueFormulaType.FLAT,
                  value = 15f,
                ),
            ),
          ),
      ),
    passive =
      PassiveDefinition(
        key = "sisterhood_banner",
        name = "Sisterhood Banner",
        trigger = PassiveTriggerType.AURA,
        condition =
          CombatConditionDefinition(
            type = CombatConditionType.ALIVE_ALLIES_WITH_TAG_AT_LEAST,
            tag = "female",
            minimumCount = 2,
          ),
        modifiers =
          listOf(
            CombatModifierDefinition(
              type = CombatModifierType.ATTACK_MULTIPLIER,
              value = 0.5f,
            ),
          ),
      ),
  )

fun rangerTemplate(key: String = "ranger"): CharacterTemplate =
  characterTemplate(
    key = key,
    name = "Ranger",
    strength = StatGrowth(8f, 1f),
    agility = StatGrowth(12f, 1.8f),
    intelligence = StatGrowth(6f, 0.8f),
    wisdom = StatGrowth(7f, 0.9f),
    vitality = StatGrowth(9f, 1.3f),
    tags = listOf("female", "ranged"),
    skill =
      SkillDefinition(
        key = "piercing_volley",
        name = "Piercing Volley",
        cooldownMillis = 4_000,
        effects =
          listOf(
            CombatEffectDefinition(
              type = CombatEffectType.DAMAGE,
              target = CombatTargetType.ENEMY,
              amount =
                CombatValueFormula(
                  type = CombatValueFormulaType.STAT_SCALING,
                  stat = CombatStatType.ATTACK,
                  multiplier = 1.7f,
                  flatBonus = 2f,
                ),
            ),
          ),
      ),
  )

fun characterTemplate(
  key: String,
  name: String,
  strength: StatGrowth,
  agility: StatGrowth,
  intelligence: StatGrowth,
  wisdom: StatGrowth,
  vitality: StatGrowth,
  tags: List<String> = emptyList(),
  skill: SkillDefinition? = null,
  passive: PassiveDefinition? = null,
): CharacterTemplate =
  CharacterTemplate(
    key = key,
    name = name,
    strength = strength,
    agility = agility,
    intelligence = intelligence,
    wisdom = wisdom,
    vitality = vitality,
    tags = tags,
    skill = skill,
    passive = passive,
  )

fun swordItem(): Item =
  Item(
    name = "sword_0001",
    displayName = "Old Dagger",
    type = ItemType.WEAPON,
    baseStat = Stat(StatType.STRENGTH, 12f),
    subStatPool = listOf(StatType.AGILITY, StatType.VITALITY, StatType.CRIT_CHANCE),
  )

fun armorItem(): Item =
  Item(
    name = "shield_0001",
    displayName = "Old Shield",
    type = ItemType.ARMOR,
    baseStat = Stat(StatType.VITALITY, 18f),
    subStatPool = listOf(StatType.STRENGTH, StatType.WISDOM, StatType.CRIT_DAMAGE),
  )
