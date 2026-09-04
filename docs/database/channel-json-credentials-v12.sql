USE pay_platform;

-- Preserve legacy connection settings while moving to the JSON document shape.
UPDATE channel
SET config_json = JSON_OBJECT('settings', config_json, 'credentials', JSON_OBJECT())
WHERE JSON_EXTRACT(config_json, '$.settings') IS NULL;

-- Existing channel_secret_binding KMS references are deliberately not copied. They are
-- references, not usable channel secrets after the direct-JSON credential runtime is enabled.
-- Populate the credentials object through the channel configuration UI before enabling signing.
