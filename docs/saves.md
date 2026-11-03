# Saves that can't lose a world (`oldfaithful-saves`)

Claude Opus 5.5, 2026-11-02. Fixes `docs/bugs.md` #1 and #2 (and #52, a server's level.dat; #43 and #65, block
entities that crash), which the user put in Purist (2026-11-02): nothing
changes in play, the files stay b1.7.3's own format (plain b1.7.3 and other tools read them, and we read theirs), and
a crash, a kill or a power cut leaves every file as it was before a save or after it, never damaged. Both editions,
client and server.

## What vanilla does wrong

- **Chunk saves** (`RegionFile.writeChunkData`): a chunk that still fits its sectors is rewritten in place; one that
  doesn't frees its old sectors first and may be written over them, header first. A crash mid-write leaves a torn
  record the header points at. A chunk of 256 sectors or more (1 MB compressed) is silently not saved, ever again.
  Nothing is forced to disk.
- **Opening a region file** (`RegionFile`'s constructor): a file whose length isn't a whole number of sectors (a
  write cut short while the file grew, or another tool) is "padded" by writing zeros at the file pointer, which is at
  the start of the file: the zeros land on the chunk index, so the chunks it listed read as never generated, are
  generated anew and saved over the real ones, whose sectors now look free.
- **Chunk loads** (`ChunkCache.loadChunkFromStorage`, `RegionChunkStorage.loadChunk`): any failure (a torn record,
  one entity whose data throws, a read error from a file another program holds) counts as "never generated": new
  terrain is generated and saved over the record. Damaged data can also crash the game outright on every load (an
  array claiming two billion bytes, an unknown tag type, a block array of the wrong size).
- **level.dat**: renamed into place without being forced to disk first (a power cut can leave it empty).
- **level.dat on a server with `allow-nether=false`** (`docs/bugs.md` #52): `MinecraftServer.loadWorld` always makes
  the Nether, a `ReadOnlyServerWorld` with its own copy of level.dat's data (`World`'s constructor reads it; on a new
  world it runs its own spawn search, landing near 0, 64, 0), and every world's save writes level.dat with its own
  copy. The tick loop skips the Nether when it's off, so its copy never advances, and `saveWorlds` (save-all, and
  stopping) saves it after the overworld: every stop puts back the time and weather the session began with (time 0,
  forever, on a world made that way), and a new world's spawn becomes the Nether's. Autosaves come only from the
  overworld, so a kill keeps the right file and a clean stop loses it. Seen on vanilla's server and Pure Faithful
  0.1.0 (2026-11-02).
- **Player files** (server): the old file is deleted, then a shared `_tmp_.dat` renamed over it; a crash in between
  loses the player's inventory and position.
- **Map files and idcounts.dat** (`data/`): overwritten in place.

## How the fix works

**Region writes** (`RegionWrites`, through `RegionFileMixin`): a chunk is always written to sectors nobody
references. The header on disk is only changed to point at it after that data has been forced to disk, and the
sectors it pointed at before are only reused after that header change has itself been forced. So at every moment
each header entry on disk points at a complete record, the new one or the previous one. The in-memory header
(vanilla's `chunkBlockInfo`, which the game and other mods read) moves at once. Forcing and header writes happen on
a background thread (`Committer`) about 100 ms after a save pass, synchronously when a region file closes and at the
end of a full save (leaving the world, save-all, stopping the server), and from a shutdown hook that keeps going until
saving has been quiet for 300 ms (so a save still running in the server's own shutdown hook is committed too). A hard
kill can lose the last ~100 ms of saves (the world as it was a moment earlier), never damage anything. Sectors are
counted by reference, including ones a damaged header claims just past the end of the file, so nothing referenced is
ever handed out, and each index's counted sectors are remembered, so releasing a damaged entry later never uncounts
sectors that by then hold someone else's record. A record that extends the file is written together with the zeros to
the end of its last sector, in one write, so the file is never left a partial sector long. Before vanilla's
constructor opens a file, a file that is a partial sector long anyway (from before, or another tool) is padded at the
end (`RegionWrites.alignBeforeOpen`, logged), so the constructor's own padding never runs.

**Oversized chunks** (`Oversized`): the record goes to `region/oldfaithful-oversized/r.X.Z.<lx>.<lz>.dat`, stamped with
the header entry and time the region held when it was written; the region keeps its previous record, which plain
b1.7.3 reads. The side file counts only while the region still holds exactly that entry, so a later save of the chunk
(here, or by plain b1.7.3) supersedes it; ours deletes it once that save is durable.

**Chunk loads** (`ChunkLoad`, through `RegionChunkStorageMixin`): the record is read and decompressed in full, the NBT
walked and every length checked before the game's parser sees it (`NbtCheck`), and the chunk's arrays checked. Then:
- what can be read is kept: an entity or block entity whose data throws is left out (`AlphaChunkStorageMixin`),
  arrays of the wrong size are rebuilt as vanilla rebuilds missing ones (sky light and height map recomputed; block
  data and block light reset);
- every one of vanilla's blocks with a block entity that has none gets a fresh one (`ChunkLoad.restoreBlockEntities`):
  an empty chest, furnace, dispenser or jukebox (its meta set to "no record"), a blank sign, a note block on its
  lowest note, a pig spawner (the block's default); listed with the repairs, so the chunk is copied aside and saved
  repaired like any other. Without it, breaking a chest, furnace, dispenser or jukebox throws: their `onBreak` reads
  the block entity without a null check, and `Chunk.setBlock` swaps the block id before calling it, so
  `Chunk.getBlockEntity`'s lazy creation no longer applies; a dedicated server stops ("Unexpected exception"). Every
  other use (opening, redstone, a note block played) goes through the lazy creation and works, though for a furnace
  or dispenser that runs `onPlaced`, which turns it to face away from its neighbours; made directly here, the facing
  is kept. A sign without one is invisible. 0.1.0 left blocks this way when it left out an unreadable block entity,
  and saved the chunk so (found 2026-11-02), which is why every loaded chunk is checked, not only damaged ones:
  vanilla's own play never leaves one of these blocks without its block entity, so a healthy world finds nothing,
  and the check is a pass over the block array. A piston's moving block (36) has no default (`createBlockEntity` is
  null) and vanilla's code for it handles none, so it's left as is, as are other mods' blocks. A lit furnace stays lit
  until it next burns something;
- a spawner set to something that isn't a mob (primed TNT, an item: world editors allow it; `docs/bugs.md` #65) is set
  back to a pig, the block's default, and listed with the repairs (`ChunkLoad.repairSpawners`). Its tick casts what it
  makes to a mob, so such a spawner threw every tick a player was within 16 blocks: the crash screen, or a server that
  stops, each time the world opens. An id nothing is registered under is left alone (the game already spawns nothing
  for it, and it may be a mod's mob that comes back with the mod);
- what can't be read at all is copied to `corrupt/` in the world (or dimension) folder, bytes exactly as found plus a
  `.txt` note, forced to disk, and only then regenerated as vanilla would, so the world goes on and the original is
  kept for repair (a damaged record has nothing better to offer, and later Minecraft versions do the same);
- a read error (nothing to copy) is retried three times; if it persists, or if the copy can't be made (a full disk,
  no permission), the chunk shows as generated terrain but is never saved this session, so the record stays on disk
  as it was; it isn't populated either, which would spill trees and ores from made-up terrain into its real
  neighbours;
- each case is logged (`[oldfaithful-saves] WARN`) and, on the client, said in chat ("Old Faithful: the chunk at
  blocks X, Z ...").
A partly damaged chunk is saved again, repaired, at the next save once its original is kept, so the repair sticks and
isn't reported every session. A healthy chunk loads exactly as vanilla loads it.

**level.dat** (`AlphaWorldStorageMixin`): the same data and the same rotation (`level.dat_old` kept), with the new
file forced before either rename, off the game thread (the game saves level.dat every 2 seconds). A crash between the
two renames leaves a complete `level.dat_new`, which loading prefers to the older `level.dat_old`. If neither
level.dat can be read, both are copied to `corrupt/` and the player told before the game writes new ones; if they
can't be copied, level.dat isn't saved this session. Renaming a world in the menu replaces level.dat whole
(`AlphaWorldStorageSourceMixin`) instead of rewriting it in place. On a server with `allow-nether=false`, the
Nether's copy of the data is marked when the world is made (`ReadOnlyServerWorldMixin`, the tick loop's own check) and
never written (`UntickedNether`); the overworld's, saved just before it and by every autosave, stands. With the Nether
on nothing changes: there vanilla's Nether still writes last, so level.dat holds its spawn (a new world's spawn moves
to near 0, 64, 0 after the first start) and its weather (frozen, the Nether has none), and a night slept through is
undone by a restart (the Nether doesn't skip it). Beta's behaviour that every server with the Nether had, left to Old
Faithful (`docs/bugs.md` #52). Single player is untouched: one world at a time, the next made from a copy of the
last's data after it's saved (`Minecraft.changeDimension`).

**Player files** (`AlphaWorldStoragePlayersMixin`, server): written to a temporary file of its own, forced, renamed
over `<name>.dat` in one step; a queued save is finished before that player's file is read; an unreadable one is
copied aside before the game replaces it, and if it can't be copied (a file another program holds is the usual
cause) that player's file isn't saved over this session. **Map files and idcounts.dat** (`PersistentStateManagerMixin`):
written to a temporary file of its own, then checked to be complete NBT (the game's NbtIo closes its stream in a
`finally`, so a save that threw halfway still closes it) before it's forced and renamed over the target; a failed
write or an incomplete file leaves the previous one.

**Pistons near the edge of the loaded world** (`PistonBlockMixin`, both sides): a crash the piston hunt found
(`docs/bugs.md` #53); in a loaded world nothing changes.

**The committer** never runs two saves of the same file at once (each has its own temporary file anyway), and a full
save's `commitAll` returns only when every save, including one another thread is running, is on disk.

## Tests

- `./gw :oldfaithful-saves:test` (about 45 s): vanilla's own `RegionFile` (the official code, no mixins) reads
  everything we write, and we continue files vanilla wrote; an uncommitted save leaves the old record and a committed
  one the new; old sectors aren't reused before the header pointing away from them is durable; damaged records (index
  past the end, impossible length, unknown compression, damaged data, truncated file, a wild index) are reported and
  not one of their bytes is touched by 400 later saves; a wild index entry replaced after the file grew past it; an
  unaligned file wipes vanilla's index (the control) and keeps every entry once aligned first; oversized chunks, and a
  side file superseded by plain b1.7.3; the NBT pre-check. `FileSafetyTest`: never two saves of one file at once and
  commitAll waits for one in flight; the newer save wins; half-written map and idcounts files never replace good ones;
  a chunk, player file or level.dat whose copy can't be kept is never saved over. **KillTest**: the writer fills 256
  chunks, then saves precompressed records flat out (so nearly all its time is in the write) and is killed outright
  (TerminateProcess) at random moments, 30 times on the same file (`-Pkills=150` for more); after each kill every one
  of the 256 must be there (a missing one counts), complete and in its place, and the file a whole number of sectors.
  Ours, 2026-11-02 after the fixes below: 0 damaged in 150 kills (38,400 entries checked). Vanilla's writer, the
  control: damaged entries after 6 of 30 kills. (An earlier run counted only entries present, read the file with the
  constructor that wipes unaligned indexes, and spent most of its time compressing; its "0 in 150" was weaker
  evidence than it looked.)
- `./gw :oldfaithful-saves:serverSaveTest` (about 45 s): the real dedicated server. Four chunks beside spawn damaged
  four ways (damaged compressed data, unknown compression, unreadable block entities, sky light of the wrong size):
  the first two are copied byte for byte to `corrupt/` and regenerated, the other two load without the bad part and
  are saved repaired, all four reported. The block-entity chunk holds a chest, furnace, dispenser, jukebox (holding a
  record), note block, sign, spawner and piston moving block, each with a field of the wrong type; a fifth, otherwise
  sound chunk a chest with no block entity at all (as 0.1.0 saved them) and a spawner of primed TNT (#65), which the
  scenario mod makes tick as if a player stood beside it. A test-only server mod (the `scenario`
  source set) then checks each has a fresh block entity of the right kind with its facing kept (none for the moving
  block), puts a diamond in each container and a record in the jukebox, and breaks them all: the diamonds and the
  record drop and the server runs on; 40 ticks later the spawner, set back to pigs, has ticked without a fault. The
  controls, before each fix: the first chest broken threw in `ChestBlock.onBreak`, and the TNT spawner's first tick
  threw a ClassCastException; either way the server stopped ("Unexpected exception"). Then the spawn region is left 300 bytes past a whole sector: the server pads
  it at the end and every chunk survives. Then the server is killed outright 8 times during bursts of save-all (about
  100 in all): after each kill, every region is whole sectors, no entry has gone, and every chunk and level.dat read
  cleanly with vanilla's reader. A final start after the kills reports nothing. (These kills land mid-write less often
  than KillTest's; KillTest is the sharper check.) Each run has a world folder of its own under
  `local/run/saves-server-test/` (kept if it fails, cleared after two hours) and kills its servers if its JVM exits:
  runs from two sessions once shared one world, and one's server saving changed region files between the other's
  check and its edits. A second test, `levelDatSurvivesWithoutTheNether` (about 20 s), starts a new world with
  `allow-nether=false` twice: while it runs, level.dat (written then by the overworld alone) is read; after each stop
  the time must not have gone back, the spawn must be the same, and the weather's countdown not reset. The control,
  the fix switched off: time 119 while running, 0 after the stop, spawn 0, 64, 0.
- The harness's game tests all carry this mod (`docs/building.md`): `clientAudit` and `clientAuditSdl` apply every
  client mixin, `clientStartupTest` makes a world, plays 100 ticks and saves on leaving (the world it left read
  cleanly, 299 chunks, checked once), `serverTest` and `multiplayerTest` run the server with it (the server package
  includes it). The benchmark (`benchSetup`) leaves it out, so its baseline stays as measured.
- `./gw :hunt:pistonEdgeTest` (about a minute; the hunt is test-only and private): #53's scenes on the real server with
  this mod (no fault, nothing lost) and without it (the control, where they fail).
- `./gw :oldfaithful-saves:test -Pmeasure`: the game-thread cost of a chunk save, ours against vanilla, in autosave's
  rhythm with background forcing going on. Measured 2026-11-02: median 1.0 ms both, p99 1.9 to 2.0 ms ours against
  2.0 to 2.1 ms vanilla (compression dominates; the forcing never stalls the writer).

Not tested here: a real power cut (the ordering argument above is the evidence; no test on this PC can cut power
between a write and the disk); the chat notice on screen (logged and queued; the start-up test's world has nothing
damaged); a file locked by another program (the retry path).

## Doubts and limits

- StationAPI replaces chunk storage with its own format, which still writes through `RegionFile` (so the crash-safe
  writes apply) but loads through its own code, so the careful load doesn't.
- Pre-region (Alpha-format) worlds go through `AlphaChunkStorage`'s own save, which vanilla converts away on opening;
  not covered.
- Temporary files from a crash mid-save (`*.oftmp`) are left behind, harmless.
- A missing or wrong-size block light array is reset to zeros, which is also what vanilla ends with (its "recompute",
  `Chunk.method_857`, is empty in b1.7.3); the light fills back in as blocks around it update.
- A region file whose header is damaged beyond a few entries isn't repaired, only kept from getting worse.
- An independent review (2026-11-02) found the unaligned-file wipe, overlapping saves of one file in the committer,
  regenerating over an original whose copy failed, half-written map files replacing good ones, uncounted wild
  entries, needless forcing of clean files on close, decoration from stand-in chunks, and the world-rename path; all
  are fixed above. The first five each have a test aimed at them (the unaligned wipe and torn writes shown failing on vanilla's
  own code as controls); the last three are argued from the
  code (decoration and the rename path need a damaged world and the menu, and close-forcing is only a cost).
