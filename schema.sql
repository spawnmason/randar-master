CREATE TABLE IF NOT EXISTS events (
    id    BIGSERIAL PRIMARY KEY,
    event JSONB     NOT NULL,

    CHECK(event ? 'type'),
    CHECK(id > 0)
);

CREATE TABLE event_progress (
    id_must_be_zero INTEGER PRIMARY KEY,
    id BIGINT, -- the id of the last event we processed, so we must query > event_progress.id to get the newest events

    CHECK(id_must_be_zero == 0)
);
INSERT INTO event_progress VALUES(0, 0) ON CONFLICT DO NOTHING;

CREATE TABLE servers
(
    id       SMALLSERIAL PRIMARY KEY,
    hostname TEXT NOT NULL UNIQUE,

    CHECK(hostname LIKE '%.%:%') -- enforce port number must be present
);

CREATE TABLE IF NOT EXISTS worlds -- one table for both servers and dimensions because of seed being different in overworld vs nether
(
    id        SMALLSERIAL PRIMARY KEY,
    server_id SMALLINT    NOT NULL,
    dimension SMALLINT    NOT NULL,
    seed      BIGINT      NOT NULL,

    UNIQUE(server_id, dimension),
    --CHECK(seed >= 0 AND seed < (1::bigint << 48)),
    CHECK(dimension >= 0 AND dimension <= 1),

    FOREIGN KEY (server_id) REFERENCES servers (id)
        ON UPDATE CASCADE ON DELETE CASCADE
);

INSERT INTO servers(hostname) VALUES('2b2t.org:25565');
INSERT INTO worlds(server_id, dimension, seed) VALUES (0, 0, -4172144997902289642) ON CONFLICT DO NOTHING; -- 2b2t overworld
INSERT INTO worlds(server_id, dimension, seed) VALUES (0, 1, 1434823964849314312) ON CONFLICT DO NOTHING; -- 2b2t end/nether

-- queue of seeds we need to reverse
CREATE TABLE IF NOT EXISTS rng_seeds_not_yet_processed (
    world_id    SMALLINT NOT NULL,
    received_at BIGINT   NOT NULL,
    rng_seed    BIGINT   NOT NULL,

    UNIQUE(world_id, received_at, rng_seed),

    CHECK(received_at > 0),
    CHECK(rng_seed >= 0 AND rng_seed < (1 << 48)),

    FOREIGN KEY (world_id) REFERENCES worlds (id)
        ON UPDATE CASCADE ON DELETE CASCADE
);

-- every seed ever
CREATE TABLE IF NOT EXISTS rng_seeds (
    world_id    SMALLINT NOT NULL,
    received_at BIGINT   NOT NULL,
    rng_seed    BIGINT   NOT NULL,

    UNIQUE(world_id, received_at, rng_seed),

    CHECK(received_at > 0),
    CHECK(rng_seed >= 0 AND rng_seed < (1 << 48)),

    FOREIGN KEY (world_id) REFERENCES worlds (id)
        ON UPDATE CASCADE ON DELETE CASCADE
);

-- the useful stuff, overworld only
CREATE TABLE IF NOT EXISTS rng_seeds_processed (
    rng_seed    BIGINT   NOT NULL PRIMARY KEY,
    steps_back  INTEGER  NOT NULL,
    world_id    SMALLINT NOT NULL,
    woodland_x  INTEGER  NOT NULL,
    woodland_z  INTEGER  NOT NULL,

    CHECK(rng_seed >= 0 AND rng_seed < (1 << 48)),
    CHECK(steps_back >= 0 AND (world_id != 0 OR steps_back <= 2742200)), -- the upper bound may be different for other seeds so only check 2b2t overworld
    CHECK(woodland_x >= -23440 AND woodland_x <= 23440),
    CHECK(woodland_z >= -23440 AND woodland_z <= 23440)
);

CREATE TABLE players
(
    id       SERIAL PRIMARY KEY,
    uuid     UUID NOT NULL UNIQUE,
    username TEXT
);

CREATE INDEX players_by_username
    ON players (username);

CREATE EXTENSION btree_gist;
CREATE TABLE player_sessions
(
    player_id INTEGER  NOT NULL,
    server_id SMALLINT NOT NULL,
    "join"    BIGINT   NOT NULL,
    "leave"   BIGINT,
    range     INT8RANGE GENERATED ALWAYS AS (
                  CASE
                      WHEN "leave" IS NULL THEN
                          INT8RANGE("join", ~(1:: BIGINT << 63), '[)')
                      ELSE
                          INT8RANGE("join", "leave", '[]')
                  END
              ) STORED,

    -- there USED to be a really based

    -- EXCLUDE USING GiST (server_id WITH =, player_id WITH =, range WITH &&),

    -- right here, but sadly it takes WAY TOO LONG to generate and keep up to date
    -- talking over an hour to recreate it after I dropped it :(
    -- also the server guarantees this anyway so
    -- also it took multiple gigabytes of disk

    FOREIGN KEY (player_id) REFERENCES players (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (server_id) REFERENCES servers (id)
        ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE INDEX player_sessions_range ON player_sessions USING GiST (server_id, range);
CREATE INDEX player_sessions_by_leave ON player_sessions (server_id, player_id, UPPER(range));