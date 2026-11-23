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
copied unchanged from the Sheets add-on's `contracts/` (the response schema
last changed in `3ace7f1`, Sheets 6.5.0, which adds the Engine's optional
`temporaryEffects`; the request schema in `6857213`).

`sheet-bren-marks.json` is a `load` of Bren recorded on 2026-11-23 with Sheets
6.5.0 (`5e885fd`) and Engine 6.6.0 (`03cc68b`, `dnd5e.rules-engine` 6.1.0),
while he was Poisoned, at Exhaustion 2, with Sacred Weapon on and a timed DM
grant "Shrine blessing" (+1 Armor Class). It is trimmed as the other
`sheet-*.json` files are and also keeps `evaluation.sheet.temporaryEffects`
and the sheet's net attack roll marks (`attackAdvantage`,
`attackDisadvantage`); the saved projection has no temporary effects.

## The characters

Mira Quill (`mira-quill`, public) is a made-up level 5 High Elf wizard
(Evoker, Sage background) with a quarterstaff, daggers, two Potions of Healing
pinned for quick use, coins, prepared spells and ritual spells in her
spellbook. Hollow Agent (`hollow-agent`) is a DM-only record without a sheet.
Revisions in the `load-*` and `save-*` files follow one session of play on
her sheet.

Bren Ashcroft (`bren-ashcroft`) is the companion mockup's made-up Human
Paladin 5 (Oath of Devotion): chain mail, shield, a longsword with Sap,
javelins and a backpack and belt pouch, Lay on Hands, Channel Divinity with
Sacred Weapon, and Find Steed and Divine Smite with one free cast each. His
`sheet-bren-load.json` shows him at 31 of 44 hit points, concentrating on
Bless, poisoned and inspired. Corin Vale (`corin-vale`) is a made-up Human
Warlock 2 with pact slots and a thrown dagger.

## Files

Each `.json` file is `{method, params, http, ms, response}`: the
`character.v3` params sent to `POST /api/services/call`, the HTTP status, the
local time in milliseconds and the host's body (the `client-service-result.v1`
envelope, or `{"error":{...}}`). `connect-*.json` are answers to
`POST /api/services/connect`; `events-addon-data-changed.txt` is what a second
player session read from `GET /api/events` during the play session.

- `load-sheet.json` is a complete answer.
- Every other answer is trimmed but keeps the shape of a
  `character-response.v3`: its envelope (`status`, `revision`, `message`,
  `messageText`, `rulesChanged`, `policy`), the whole `state.inputs`,
  `state.rules` and `state.operationId`, and only the members of
  `state.projection.sheet` that differ from the answer before it in the
  session. Other large parts are emptied to `{}` or `[]`: the projection's
  `explanations` and `evidence`, and `evaluation`'s `guidance`, `plan`,
  `spellOptions`, `explanations`, `evidence`, `sheet` (except its `status`)
  and, when a state is present, `inputs`. The real answers carry all of it.
- A build save's `params.inputs` are the `state.inputs` of the answer before
  it with one field changed.
- `sheet-*.json` keep what the sheet view reads: the whole `state.inputs`,
  the sheet members it shows, the spell evidence and the names of classes,
  subclasses, species, backgrounds and feats, and `evaluation`'s condition
  options and spell options (`castSlots`, `ritualIds`, granted spells,
  slots). Everything else is emptied as above.

Files are written as one line; pretty-print them to read.

Compendium text (summaries, descriptions, excerpts, rules text, materials and
long casting times) is replaced by `Placeholder: compendium text removed.`;
names, IDs and numbers are kept. Actor IDs are replaced by `<role>-actor-<n>`.
Engine and Sheets wording (messages, explanation formulas) is unchanged.
