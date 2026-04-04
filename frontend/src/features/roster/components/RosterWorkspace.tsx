import type { ClientOwnedCharacter } from '../../session/model/clientModels'

interface RosterWorkspaceProps {
  ownedCharacters: ClientOwnedCharacter[]
}

export function RosterWorkspace({ ownedCharacters }: RosterWorkspaceProps) {
  return (
    <>
      <section className="workspace-main panel">
        <section className="workspace-section">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Roster</p>
              <h2>Character Unlocks</h2>
            </div>
            <span className="section-count">{ownedCharacters.length}</span>
          </div>

          <div className="card-grid">
            {ownedCharacters.map((character) => (
              <article className="workspace-card" key={character.key}>
                <span className="label">{character.key}</span>
                <strong>{character.name}</strong>
                <p>Level {character.level}</p>
              </article>
            ))}
          </div>
        </section>
      </section>

      <aside className="workspace-context panel">
        <section className="workspace-section">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Context</p>
              <h2>Character Notes</h2>
            </div>
          </div>

          <div className="stack-panel">
            <article className="workspace-card">
              <span className="label">Unlocked</span>
              <strong>{ownedCharacters.length}</strong>
              <p>Owned characters currently share the player level.</p>
            </article>
            <article className="workspace-card">
              <span className="label">Starting Roster</span>
              <strong>{ownedCharacters[0]?.name ?? 'Warrior'}</strong>
              <p>Future character details can live here.</p>
            </article>
          </div>
        </section>
      </aside>
    </>
  )
}
