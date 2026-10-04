CREATE TABLE IF NOT EXISTS summer_security_keys (
    kid VARCHAR(128) NOT NULL PRIMARY KEY,
    algorithm VARCHAR(32) NOT NULL,
    ciphertext TEXT NOT NULL,
    version INT NOT NULL,
    status VARCHAR(32) NOT NULL
);
