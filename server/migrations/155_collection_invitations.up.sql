CREATE TABLE collection_invitations (
    invitation_id BIGSERIAL PRIMARY KEY,
    collection_id INTEGER NOT NULL REFERENCES collections(collection_id) ON DELETE CASCADE,
    to_user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    invited_by_user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    encrypted_key TEXT NOT NULL,
    role_type role_enum NOT NULL,
    shared_at BIGINT NOT NULL,
    UNIQUE (collection_id, to_user_id)
);

CREATE INDEX collection_invitations_recipient_index ON collection_invitations (to_user_id, invitation_id DESC);

CREATE TABLE trusted_sharers (
    to_user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    sharer_user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    PRIMARY KEY (to_user_id, sharer_user_id)
);

CREATE INDEX trusted_sharers_sharer_index ON trusted_sharers (sharer_user_id);
