CREATE TABLE IF NOT EXISTS events (
    id    BIGSERIAL PRIMARY KEY,
    event JSONB     NOT NULL,

    CHECK(event ? 'type'),
    CHECK(id > 0)
);

CREATE TABLE IF NOT EXISTS event_progress (
    id_must_be_zero INTEGER PRIMARY KEY,
    id BIGINT NOT NULL, -- the id of the last event we processed, so we must query > event_progress.id to get the newest events

    CHECK(id_must_be_zero = 0)
);
INSERT INTO event_progress VALUES(0, 0) ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS sqlite_backfill_progress (
    id_must_be_zero INTEGER PRIMARY KEY,
    last_rowid_processed BIGINT,

    CHECK(id_must_be_zero = 0)
);
INSERT INTO sqlite_backfill_progress VALUES(0, 0) ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS servers (
    id   SMALLSERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS worlds ( -- one table for both servers and dimensions because of seed being different in overworld vs nether
    server_id SMALLINT    NOT NULL,
    dimension SMALLINT    NOT NULL,
    seed      BIGINT      NOT NULL,

    UNIQUE(server_id, dimension),
    --CHECK(seed >= 0 AND seed < (1::bigint << 48)),
    CHECK(dimension >= 0 AND dimension <= 1),

    FOREIGN KEY (server_id) REFERENCES servers (id)
        ON UPDATE CASCADE ON DELETE CASCADE
    -- TODO add a check that worlds.id=0 means the server is 2b2t overworld, to back up the CHECK on world_id=0 in rng_seeds_processed
);

INSERT INTO servers(name) VALUES('2b2t.org') ON CONFLICT DO NOTHING;
INSERT INTO worlds(server_id, dimension, seed) VALUES (1, 0, -4172144997902289642) ON CONFLICT DO NOTHING; -- 2b2t overworld
INSERT INTO worlds(server_id, dimension, seed) VALUES (1, 1, 1434823964849314312) ON CONFLICT DO NOTHING; -- 2b2t end/nether

CREATE TABLE IF NOT EXISTS rng_seeds_not_yet_processed ( -- queue of seeds we need to reverse
    server_id   SMALLINT NOT NULL,
    dimension   SMALLINT NOT NULL,
    received_at BIGINT   NOT NULL,
    rng_seed    BIGINT   NOT NULL,

    UNIQUE(server_id, dimension, received_at, rng_seed),

    CHECK(received_at > 0),
    CHECK(rng_seed >= 0 AND rng_seed < (1::bigint << 48)),

    FOREIGN KEY (server_id) REFERENCES servers (id)
        ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS rng_seeds (
    server_id   SMALLINT NOT NULL,
    dimension   SMALLINT NOT NULL,
    received_at BIGINT   NOT NULL,
    rng_seed    BIGINT   NOT NULL,
    steps_back  INTEGER  NOT NULL,
    structure_x INTEGER  NOT NULL,
    structure_z INTEGER  NOT NULL,

    UNIQUE(server_id, dimension, received_at, rng_seed),

    CHECK(received_at > 0),
    CHECK(rng_seed >= 0 AND rng_seed < (1::bigint << 48)),
    CHECK(steps_back >= 0 AND ((server_id != 0 OR dimension != 0) OR steps_back <= 2742200)), -- the upper bound may be different for other seeds so only check 2b2t overworld
    CHECK(structure_x >= -23440 * (dimension * 3 + 1) AND structure_x <= 23440 * (dimension * 3 + 1)),
    CHECK(structure_z >= -23440 * (dimension * 3 + 1) AND structure_z <= 23440 * (dimension * 3 + 1)),

    FOREIGN KEY (server_id) REFERENCES servers (id)
        ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS players (
    id       SERIAL PRIMARY KEY,
    uuid     UUID NOT NULL UNIQUE,
    username TEXT DEFAULT NULL
);

CREATE INDEX IF NOT EXISTS players_by_username ON players (username);

CREATE TABLE IF NOT EXISTS name_history (
  player_id INTEGER NOT NULL,
  username  TEXT NOT NULL,
  time      BIGINT NOT NULL,

  FOREIGN KEY (player_id) REFERENCES players (id)
      ON UPDATE CASCADE ON DELETE CASCADE
);


CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE TABLE IF NOT EXISTS player_sessions (
    player_id INTEGER  NOT NULL,
    server_id SMALLINT NOT NULL,
    enter     BIGINT   NOT NULL,
    exit      BIGINT,
    range     INT8RANGE GENERATED ALWAYS AS (
                  CASE
                      WHEN exit IS NULL THEN
                          INT8RANGE(enter, ~(1:: BIGINT << 63), '[)')
                      ELSE
                          INT8RANGE(enter, exit, '[]')
                  END
              ) STORED,

    EXCLUDE USING GiST (server_id WITH =, player_id WITH =, range WITH &&),

    FOREIGN KEY (player_id) REFERENCES players (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (server_id) REFERENCES servers (id)
        ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS player_sessions_range ON player_sessions USING GiST (server_id, range);
CREATE INDEX IF NOT EXISTS player_sessions_by_leave ON player_sessions (server_id, player_id, UPPER(range));

CREATE OR REPLACE VIEW online_players AS SELECT * FROM player_sessions WHERE range @> (~(1::bigint << 63) >> 1);