-- The desktop app gets an extension credential of its own, next to the browser extension's: one per
-- user and client. Existing credentials are the browser extension's.
ALTER TABLE extension_credentials ADD COLUMN client VARCHAR(16) DEFAULT 'BROWSER' NOT NULL;
-- H2 lets the foreign key take over the old unique index on user_id, so it goes too, and comes back
-- on the new index.
ALTER TABLE extension_credentials DROP CONSTRAINT fk_extension_credentials_user;
ALTER TABLE extension_credentials DROP CONSTRAINT uq_extension_credentials_user;
ALTER TABLE extension_credentials ADD CONSTRAINT uq_extension_credentials_user_client UNIQUE (user_id, client);
ALTER TABLE extension_credentials ADD CONSTRAINT fk_extension_credentials_user
    FOREIGN KEY (user_id) REFERENCES users (id);
