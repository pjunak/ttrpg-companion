# Character service recordings

Real answers of the `dnd5e.character` **4.0.0** service (requests
`character.v3`, answers `character-response.v3`), recorded on 2026-11-23
through the host's client service routes against a throwaway local host.

| Part | Version | Commit |
| --- | --- | --- |
| Host (`ttrpg-codex`) | 2.2.0 | `a70c1e5` |
| Character Sheets (`dnd-sheets`, provides `dnd5e.character`) | 6.4.0 | `5bd9047` |
| Engine (`dnd-engine`, provides `dnd5e.rules-engine` 6.0.0) | 6.5.0 | `06551de` |
| D&D 2024 Compendium | 3.8.0 | `6b0f079` |

`character.request.schema.json` and `character.response.schema.json` are
copied unchanged from the Sheets add-on's `contracts/` (last changed in
`6857213`; identical to the installed 6.4.0 package).

## The character

Mira Quill (`mira-quill`, public) is a made-up level 5 High Elf wizard
(Evoker, Sage background) with a quarterstaff, daggers, two Potions of Healing
pinned for quick use, coins, prepared spells and ritual spells in her
spellbook. Hollow Agent (`hollow-agent`) is a DM-only record without a sheet.
Revisions in the files follow one session of play on her sheet.

## Files

Each `.json` file is `{method, params, http, ms, response}`: the
`character.v3` params sent to `POST /api/services/call`, the HTTP status, the
local time in milliseconds and the host's body (the `client-service-result.v1`
envelope, or `{"error":{...}}`). `connect-*.json` are answers to
`POST /api/services/connect`; `events-addon-data-changed.txt` is what a second
player session read from `GET /api/events` during the play session.

- `load-sheet.json` and `save-play-cast-spell.json` are complete answers.
- In every other answer, `result` keeps its envelope (`status`, `revision`,
  `message`, `messageText`, `rulesChanged`, `policy`) and only the parts of
  `state` that differ from the answer before it in the session
  (`state.inputs.play`, `state.projection.sheet` and `state.rules` keys);
  `evaluation` is dropped except the blank `inputs` of a record without a
  sheet. The real answers carry the whole saved state.

Compendium text (summaries, descriptions, excerpts, rules text, materials and
long casting times) is replaced by `Placeholder: compendium text removed.`;
names, IDs and numbers are kept. Actor IDs are replaced by `<role>-actor-<n>`.
Engine and Sheets wording (messages, explanation formulas) is unchanged.
