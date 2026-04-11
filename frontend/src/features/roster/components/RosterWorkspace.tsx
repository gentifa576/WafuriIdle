import { useEffect, useMemo, useState } from 'react'
import type { ClientCharacterTemplate, ClientOwnedCharacter, ClientStatGrowth } from '../../session/model/clientModels'
import {
  describePassive,
  formatGrowth,
  growthAtLevel,
  mapCharacterDisplayModels,
  type CharacterDisplayModel,
} from '../../session/model/characterDisplay'
import { ActionButton } from '../../../shared/ui/ActionButton'
import { HoverPreviewCard } from '../../../shared/ui/HoverPreviewCard'
import { SectionHeader } from '../../../shared/ui/SectionHeader'
import { SurfaceCard } from '../../../shared/ui/SurfaceCard'
import { createHoverTileHandlers, hoverPreviewStyle } from '../../../shared/ui/hoverPreview'
import { useDelayedHover } from '../../../shared/ui/useDelayedHover'
import { CharacterRosterTile } from './CharacterRosterTile'
import './roster.css'

interface RosterWorkspaceProps {
  ownedCharacters: ClientOwnedCharacter[]
  templates: ClientCharacterTemplate[]
}

export function RosterWorkspace({ ownedCharacters, templates }: RosterWorkspaceProps) {
  const [selectedCharacterKey, setSelectedCharacterKey] = useState<string | null>(null)
  const hover = useDelayedHover<{ key: string }>({
    matches: (current, target) => current.key === target.key,
  })
  const roster = useMemo(() => mapCharacterDisplayModels(ownedCharacters, templates), [ownedCharacters, templates])
  const selectedCharacter = roster.find((character) => character.key === selectedCharacterKey) ?? null
  const hoveredCharacter = roster.find((character) => character.key === hover.hoverState?.key) ?? null

  useEffect(() => {
    if (selectedCharacterKey && !roster.some((character) => character.key === selectedCharacterKey)) {
      setSelectedCharacterKey(null)
    }
  }, [roster, selectedCharacterKey])

  return (
    <>
      <section className="workspace-main panel roster-shell">
        <section className="workspace-section roster-grid-section">
          {selectedCharacter ? (
            <>
              <SectionHeader
                eyebrow="Roster"
                title={selectedCharacter.name}
                aside={
                  <ActionButton onClick={() => setSelectedCharacterKey(null)} slim variant="ghost">
                    Back
                  </ActionButton>
                }
              />

              <div className="roster-scroll-body">
                <div className="roster-detail-layout">
                  <div className="roster-detail-stage">
                    <div className="roster-detail-header">
                      <CharacterRosterTile
                        compact
                        image={selectedCharacter.image}
                        name={selectedCharacter.name}
                        selected
                      />
                    </div>

                    <SurfaceCard className="roster-detail-panel">
                      <div className="roster-detail-summary">
                        <span className="label">Character Details</span>
                        <h3>{selectedCharacter.name}</h3>
                        <p>Level {selectedCharacter.level}</p>
                        <p className="muted">
                          Roster member with {selectedCharacter.tags.length || 'no'} authored tag{selectedCharacter.tags.length === 1 ? '' : 's'}.
                        </p>
                      </div>

                      <div className="roster-stat-grid">
                        {statEntries(selectedCharacter).map((stat) => (
                          <div className="roster-stat" key={stat.label}>
                            <span className="label">{stat.label}</span>
                            <strong>{stat.value}</strong>
                            <small className="muted">Base {stat.base} + {stat.increment}/lv</small>
                          </div>
                        ))}
                      </div>

                      <div className="roster-detail-meta">
                        <SurfaceCard className="roster-info-card">
                          <span className="label">Combat Kit</span>
                          <h3>{selectedCharacter.skill?.name ?? 'No skill configured'}</h3>
                          <div className="roster-kit-copy">
                            <p className="muted">
                              {selectedCharacter.skill
                                ? `${Math.round(selectedCharacter.skill.cooldownMillis / 100) / 10}s cooldown`
                                : 'This character currently has no authored active skill.'}
                            </p>
                            <p>
                              Passive: <strong>{selectedCharacter.passive?.name ?? 'No passive configured'}</strong>
                            </p>
                            {selectedCharacter.passive ? <p className="muted">{describePassive(selectedCharacter.passive)}</p> : null}
                          </div>
                        </SurfaceCard>

                        <SurfaceCard className="roster-info-card">
                          <span className="label">Identity</span>
                          <h3>{selectedCharacter.key}</h3>
                          <div className="roster-kit-copy">
                            <p className="muted">Owned characters currently scale with the player level.</p>
                            <div className="roster-tags">
                              {selectedCharacter.tags.length > 0 ? (
                                selectedCharacter.tags.map((tag) => (
                                  <span className="roster-tag" key={tag}>
                                    {tag}
                                  </span>
                                ))
                              ) : (
                                <span className="muted">No authored tags</span>
                              )}
                            </div>
                          </div>
                        </SurfaceCard>
                      </div>
                    </SurfaceCard>
                  </div>
                </div>
              </div>
            </>
          ) : (
            <>
              <SectionHeader eyebrow="Roster" title="Character Unlocks" />

              <div className="roster-scroll-body">
                {roster.length > 0 ? (
                  <div className="card-grid roster-grid">
                    {roster.map((character) => (
                      <CharacterRosterTile
                        badge={`Lv ${character.level}`}
                        image={character.image}
                        key={character.key}
                        name={character.name}
                        onClick={() => {
                          hover.clear()
                          setSelectedCharacterKey(character.key)
                        }}
                        {...createHoverTileHandlers(hover, { key: character.key })}
                      />
                    ))}
                  </div>
                ) : (
                  <SurfaceCard>
                    <span className="label">No Unlocks</span>
                    <strong>Your roster is empty.</strong>
                    <p>Claim a starter or pull characters from the gacha to populate this view.</p>
                  </SurfaceCard>
                )}
              </div>
            </>
          )}
        </section>
      </section>

      <aside className="workspace-context panel">
        <section className="workspace-section">
          <SectionHeader eyebrow="Context" title="Roster Notes" />

          <div className="stack-panel">
            <SurfaceCard>
              <div className="roster-side-stat">
                <span className="label">Unlocked</span>
                <strong>
                  {roster.length}/{templates.length}
                </strong>
              </div>
              <p>{selectedCharacter ? `${selectedCharacter.name} is in focus.` : 'Select a tile to inspect the full character sheet.'}</p>
            </SurfaceCard>
          </div>
        </section>
      </aside>

      {hoveredCharacter ? (
        <HoverPreviewCard
          className="roster-hover-card"
          headerClassName="roster-hover-header"
          style={hoverPreviewStyle(hover.hoverState!)}
          title={hoveredCharacter.name}
          badge={`Lv ${hoveredCharacter.level}`}
        >
          <p className="muted">{hoveredCharacter.key}</p>
          <div className="roster-tags">
            {hoveredCharacter.tags.length > 0 ? (
              hoveredCharacter.tags.map((tag) => (
                <span className="roster-tag" key={tag}>
                  {tag}
                </span>
              ))
            ) : (
              <span className="muted">No tags</span>
            )}
          </div>
          <p>
            STR {growthAtLevel(hoveredCharacter.strength, hoveredCharacter.level)} · AGI {growthAtLevel(hoveredCharacter.agility, hoveredCharacter.level)} · VIT{' '}
            {growthAtLevel(hoveredCharacter.vitality, hoveredCharacter.level)}
          </p>
          <p className="muted">
            Skill: {hoveredCharacter.skill?.name ?? 'None'} | Passive: {hoveredCharacter.passive?.name ?? 'None'}
          </p>
        </HoverPreviewCard>
      ) : null}
    </>
  )
}

function statEntries(character: CharacterDisplayModel) {
  return [
    toStatEntry('Strength', character.strength, character.level),
    toStatEntry('Agility', character.agility, character.level),
    toStatEntry('Intelligence', character.intelligence, character.level),
    toStatEntry('Wisdom', character.wisdom, character.level),
    toStatEntry('Vitality', character.vitality, character.level),
  ]
}

function toStatEntry(label: string, growth: ClientStatGrowth, level: number) {
  return {
    label,
    base: formatGrowth(growth.base),
    increment: formatGrowth(growth.increment),
    value: growthAtLevel(growth, level),
  }
}
