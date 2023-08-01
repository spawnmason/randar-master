CREATE TABLE IF NOT EXISTS events
(
    id    BIGSERIAL PRIMARY KEY,
    event JSONB NOT NULL,

    CHECK (event ? 'type'),
    CHECK (id > 0)
);

CREATE TABLE IF NOT EXISTS event_progress
(
    id_must_be_zero INTEGER PRIMARY KEY,
    id              BIGINT NOT NULL, -- the id of the last event we processed, so we must query > event_progress.id to get the newest events

    CHECK (id_must_be_zero = 0)
);
INSERT INTO event_progress
VALUES (0, 0)
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS sqlite_backfill_progress
(
    id_must_be_zero      INTEGER PRIMARY KEY,
    last_rowid_processed BIGINT NOT NULL,

    CHECK (id_must_be_zero = 0)
);
INSERT INTO sqlite_backfill_progress
VALUES (0, 0)
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS seed_copy_progress
(
    id_must_be_zero INTEGER PRIMARY KEY,
    id              BIGINT NOT NULL
);
INSERT INTO seed_copy_progress
VALUES (0, 0)
ON CONFLICT DO NOTHING;


CREATE TABLE IF NOT EXISTS servers
(
    id   SMALLSERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS worlds
( -- one table for both servers and dimensions because of seed being different in overworld vs nether
    server_id SMALLINT NOT NULL,
    dimension SMALLINT NOT NULL,
    seed      BIGINT   NOT NULL,

    UNIQUE (server_id, dimension),
    --CHECK(seed >= 0 AND seed < (1::bigint << 48)),
    CHECK (dimension >= 0 AND dimension <= 1),

    FOREIGN KEY (server_id) REFERENCES servers (id)
        ON UPDATE CASCADE ON DELETE CASCADE
    -- TODO add a check that worlds.id=0 means the server is 2b2t overworld, to back up the CHECK on world_id=0 in rng_seeds_processed
);

INSERT INTO servers(name)
VALUES ('2b2t.org')
ON CONFLICT DO NOTHING;
INSERT INTO worlds(server_id, dimension, seed)
VALUES (1, 0, -4172144997902289642)
ON CONFLICT DO NOTHING; -- 2b2t overworld
INSERT INTO worlds(server_id, dimension, seed)
VALUES (1, 1, 1434823964849314312)
ON CONFLICT DO NOTHING; -- 2b2t end/nether

CREATE TABLE IF NOT EXISTS rng_seeds_not_yet_processed
( -- queue of seeds we need to reverse
    id          BIGSERIAL PRIMARY KEY,
    server_id   SMALLINT NOT NULL,
    dimension   SMALLINT NOT NULL,
    received_at BIGINT   NOT NULL,
    rng_seed    BIGINT   NOT NULL,

    UNIQUE (server_id, dimension, received_at, rng_seed),

    CHECK (received_at > 0),
    CHECK (rng_seed >= 0 AND rng_seed < (1::bigint << 48)),

    FOREIGN KEY (server_id) REFERENCES servers (id)
        ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS rng_seeds
(
    server_id   SMALLINT NOT NULL,
    dimension   SMALLINT NOT NULL,
    received_at BIGINT   NOT NULL,
    rng_seed    BIGINT   NOT NULL,
    steps_back  INTEGER  NOT NULL,
    structure_x INTEGER  NOT NULL,
    structure_z INTEGER  NOT NULL,

    UNIQUE (server_id, dimension, received_at, rng_seed),

    CHECK (received_at > 0),
    CHECK (rng_seed >= 0 AND rng_seed < (1::bigint << 48)),
    CHECK (steps_back >= 0 AND
           ((server_id != 0 OR dimension != 0) OR steps_back <= 2742200)), -- the upper bound may be different for other seeds so only check 2b2t overworld
    CHECK (structure_x >= -23440 * (dimension * 3 + 1) AND structure_x <= 23440 * (dimension * 3 + 1)),
    CHECK (structure_z >= -23440 * (dimension * 3 + 1) AND structure_z <= 23440 * (dimension * 3 + 1)),

    FOREIGN KEY (server_id) REFERENCES servers (id)
        ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS rng_seeds_by_x_z ON rng_seeds (server_id, dimension, structure_x, structure_z, received_at);

CREATE TABLE IF NOT EXISTS time_aggregated_seeds
(
    timescale SMALLINT NOT NULL,
    server_id SMALLINT NOT NULL,
    dimension SMALLINT NOT NULL,
    time_idx  INTEGER  NOT NULL,
    x         INTEGER  NOT NULL,
    z         INTEGER  NOT NULL,
    quantity  INTEGER  NOT NULL,

    UNIQUE (server_id, dimension, timescale, time_idx, x, z),

    CHECK (timescale >= 0),
    CHECK (dimension = 0 OR dimension = 1),
    CHECK (time_idx >= 0),
    CHECK (x >= -23440 * (dimension * 3 + 1) AND x <= 23440 * (dimension * 3 + 1)),
    CHECK (z >= -23440 * (dimension * 3 + 1) AND z <= 23440 * (dimension * 3 + 1)),
    CHECK (quantity > 0),
    CHECK ((timescale = 0) = (time_idx = 0)),

    FOREIGN KEY (server_id) REFERENCES servers (id)
        ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS annotations
(
    server_id  SMALLINT NOT NULL,
    dimension  SMALLINT NOT NULL,
    x          INTEGER  NOT NULL,
    z          INTEGER  NOT NULL,
    created_at BIGINT   NOT NULL,
    title      TEXT, -- the title being null means that you don't want it, or no longer want it, to show up on the heatmap
    body       TEXT, -- no need to have a body either, maybe both title and body are null if you want to remove the annotation entirely

    CHECK (dimension = 0 OR dimension = 1),
    CHECK (x >= -23440 * (dimension * 3 + 1) AND x <= 23440 * (dimension * 3 + 1)),
    CHECK (z >= -23440 * (dimension * 3 + 1) AND z <= 23440 * (dimension * 3 + 1)),
    CHECK (created_at > 0),

    FOREIGN KEY (server_id) REFERENCES servers (id)
        ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE UNIQUE INDEX IF NOT EXISTS annotations_by_server_dimension_x_z_created_at ON annotations (server_id, dimension, x, z, created_at);

CREATE TABLE IF NOT EXISTS tracked_points
(
    title      TEXT,
    x          INTEGER  NOT NULL,
    z          INTEGER  NOT NULL
);

CREATE TABLE IF NOT EXISTS players
(
    id   SERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE
);

CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE IF NOT EXISTS name_history
(
    player_id   INTEGER NOT NULL,
    username    CITEXT  NOT NULL,
    observed_at BIGINT  NOT NULL,

    FOREIGN KEY (player_id) REFERENCES players (id)
        ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS name_history_by_player_id ON name_history (player_id);
CREATE INDEX IF NOT EXISTS name_history_by_username ON name_history (username);


CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE TABLE IF NOT EXISTS player_sessions
(
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

CREATE OR REPLACE VIEW online_players AS
SELECT *
FROM player_sessions
WHERE range @> (~(1::bigint << 63) >> 1);

CREATE TABLE IF NOT EXISTS associator_progress
(
    max_timestamp_processed BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS associations
(
    server_id   SMALLINT NOT NULL,
    dimension   SMALLINT NOT NULL,
    x           INTEGER  NOT NULL,
    z           INTEGER  NOT NULL,
    player_id   INTEGER  NOT NULL,
    denominator SMALLINT NOT NULL, -- "among how many players is this association shared". the bigger the number the weaker the association. in other words, strength is 1/denominator
    created_at  BIGINT   NOT NULL,

    CHECK (dimension = 0 OR dimension = 1),
    CHECK (x >= -23440 * (dimension * 3 + 1) AND x <= 23440 * (dimension * 3 + 1)),
    CHECK (z >= -23440 * (dimension * 3 + 1) AND z <= 23440 * (dimension * 3 + 1)),
    CHECK (created_at > 0),
    CHECK (denominator > 0),

    FOREIGN KEY (server_id) REFERENCES servers (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (player_id) REFERENCES players (id)
        ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS associations_player_id
    ON associations (player_id, created_at);
CREATE INDEX IF NOT EXISTS associations_location
    ON associations (x, z, created_at);
