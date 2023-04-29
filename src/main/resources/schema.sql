CREATE TABLE IF NOT EXISTS events (
    id    BIGSERIAL PRIMARY KEY,
    event JSONB     NOT NULL,

    CHECK(event ? 'type')
);

CREATE TABLE IF NOT EXISTS worlds -- one table for both servers and dimensions because of seed being different in overworld vs nether
(
    id        SMALLSERIAL PRIMARY KEY,
    hostname  TEXT        NOT NULL,
    dimension SMALLINT    NOT NULL,
    seed      BIGINT      NOT NULL,

    UNIQUE(hostname, dimension),
    --CHECK(seed >= 0 AND seed < (1::bigint << 48)),
    CHECK(dimension >= 0 AND dimension <= 1),
    CHECK(hostname LIKE '%.%:%') -- enforce port number must be present
);
INSERT INTO worlds(hostname, dimension, seed) VALUES ('2b2t.org:25565', 0, -4172144997902289642); -- 2b2t overworld
INSERT INTO worlds(hostname, dimension, seed) VALUES ('2b2t.org:25565', 1, 146008555100680); -- 2b2t end/nether

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

CREATE TABLE IF NOT EXISTS rng_seeds_processed (
    rng_seed    BIGINT   NOT NULL PRIMARY KEY,
    steps_back  INTEGER  NOT NULL,
    world_id    SMALLINT NOT NULL,
    structure_x INTEGER  NOT NULL,
    structure_z INTEGER  NOT NULL,

    CHECK(rng_seed >= 0 AND rng_seed < (1 << 48)),
    CHECK(steps_back >= 0 AND steps_back <= 2742200), -- TODO: unlikely but end could be bigger
    CHECK(structure_x >= (-23440 * 4) AND structure_x <= (23440 * 4)),
    CHECK(structure_z >= (-23440 * 4) AND structure_z <= (23440 * 4))
);