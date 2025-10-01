ALTER TABLE wallets
ADD COLUMN derivation_salt VARCHAR(255) NOT NULL DEFAULT '';

UPDATE wallets
SET derivation_salt = MD5(RANDOM()::TEXT || user_id || NOW()::TEXT);