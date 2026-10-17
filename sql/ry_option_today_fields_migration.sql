-- Optional migration for databases where the earlier 8-table schema has already been executed.
-- Run this file instead of the CREATE TABLE statements in ry_option_today.sql for those existing tables.
-- Assumes the earlier table definitions still have generated_at as the common datetime column.
-- For a fresh database, use ry_option_today.sql and do not run this migration.

ALTER TABLE `option_feed`
  CHANGE COLUMN `generated_at` `source_generated_at` datetime DEFAULT NULL COMMENT '接口数据生成时间/导入时间',
  CHANGE COLUMN `payload` `raw_payload` longtext NOT NULL COMMENT '接口完整原始 JSON 响应',
  ADD COLUMN `schema_version` JSON NULL COMMENT '响应字段：schema_version',
  ADD COLUMN `response_generated_at` JSON NULL COMMENT '响应字段：generated_at',
  ADD COLUMN `serverReceivedAt` JSON NULL COMMENT '响应字段：serverReceivedAt',
  ADD COLUMN `polledAt` JSON NULL COMMENT '响应字段：polledAt',
  ADD COLUMN `ageSeconds` JSON NULL COMMENT '响应字段：ageSeconds',
  ADD COLUMN `access` JSON NULL COMMENT '响应字段：access',
  ADD COLUMN `mode` JSON NULL COMMENT '响应字段：mode',
  ADD COLUMN `etDay` JSON NULL COMMENT '响应字段：etDay',
  ADD COLUMN `marketEtDay` JSON NULL COMMENT '响应字段：marketEtDay',
  ADD COLUMN `marketPhase` JSON NULL COMMENT '响应字段：marketPhase',
  ADD COLUMN `usingPriorTradingDay` JSON NULL COMMENT '响应字段：usingPriorTradingDay',
  ADD COLUMN `todayDesk` JSON NULL COMMENT '响应字段：todayDesk',
  ADD COLUMN `brain` JSON NULL COMMENT '响应字段：brain',
  ADD COLUMN `publicIntel` JSON NULL COMMENT '响应字段：publicIntel',
  ADD COLUMN `signals` JSON NULL COMMENT '响应字段：signals',
  ADD COLUMN `buyerSignals` JSON NULL COMMENT '响应字段：buyerSignals',
  ADD COLUMN `signalGroups` JSON NULL COMMENT '响应字段：signalGroups',
  ADD COLUMN `signalNews` JSON NULL COMMENT '响应字段：signalNews',
  ADD COLUMN `voiceFeed` JSON NULL COMMENT '响应字段：voiceFeed',
  ADD COLUMN `managementCards` JSON NULL COMMENT '响应字段：managementCards',
  ADD COLUMN `protections` JSON NULL COMMENT '响应字段：protections',
  ADD COLUMN `sentProtections` JSON NULL COMMENT '响应字段：sentProtections',
  ADD COLUMN `buyerAlerts` JSON NULL COMMENT '响应字段：buyerAlerts',
  ADD COLUMN `buyerLifecycle` JSON NULL COMMENT '响应字段：buyerLifecycle',
  ADD COLUMN `buyerExpiryCloses` JSON NULL COMMENT '响应字段：buyerExpiryCloses',
  ADD COLUMN `midtermShadowCards` JSON NULL COMMENT '响应字段：midtermShadowCards',
  ADD COLUMN `withdrawnCards` JSON NULL COMMENT '响应字段：withdrawnCards',
  ADD COLUMN `orderIntents` JSON NULL COMMENT '响应字段：orderIntents',
  ADD COLUMN `settlementCards` JSON NULL COMMENT '响应字段：settlementCards',
  ADD COLUMN `history` JSON NULL COMMENT '响应字段：history',
  ADD COLUMN `historyFingerprint` JSON NULL COMMENT '响应字段：historyFingerprint',
  ADD COLUMN `events` JSON NULL COMMENT '响应字段：events',
  ADD COLUMN `performance` JSON NULL COMMENT '响应字段：performance',
  ADD COLUMN `scan` JSON NULL COMMENT '响应字段：scan',
  ADD COLUMN `source_id` JSON NULL COMMENT '响应字段：source_id',
  ADD COLUMN `status` JSON NULL COMMENT '响应字段：status',
  ADD COLUMN `sourceHealth` JSON NULL COMMENT '响应字段：sourceHealth',
  ADD COLUMN `systemStatus` JSON NULL COMMENT '响应字段：systemStatus',
  ADD COLUMN `terminalHealth` JSON NULL COMMENT '响应字段：terminalHealth';

UPDATE `option_feed`
SET `schema_version` = CAST(JSON_EXTRACT(`raw_payload`, '$.schema_version') AS JSON),
    `response_generated_at` = CAST(JSON_EXTRACT(`raw_payload`, '$.generated_at') AS JSON),
    `serverReceivedAt` = CAST(JSON_EXTRACT(`raw_payload`, '$.serverReceivedAt') AS JSON),
    `polledAt` = CAST(JSON_EXTRACT(`raw_payload`, '$.polledAt') AS JSON),
    `ageSeconds` = CAST(JSON_EXTRACT(`raw_payload`, '$.ageSeconds') AS JSON),
    `access` = CAST(JSON_EXTRACT(`raw_payload`, '$.access') AS JSON),
    `mode` = CAST(JSON_EXTRACT(`raw_payload`, '$.mode') AS JSON),
    `etDay` = CAST(JSON_EXTRACT(`raw_payload`, '$.etDay') AS JSON),
    `marketEtDay` = CAST(JSON_EXTRACT(`raw_payload`, '$.marketEtDay') AS JSON),
    `marketPhase` = CAST(JSON_EXTRACT(`raw_payload`, '$.marketPhase') AS JSON),
    `usingPriorTradingDay` = CAST(JSON_EXTRACT(`raw_payload`, '$.usingPriorTradingDay') AS JSON),
    `todayDesk` = CAST(JSON_EXTRACT(`raw_payload`, '$.todayDesk') AS JSON),
    `brain` = CAST(JSON_EXTRACT(`raw_payload`, '$.brain') AS JSON),
    `publicIntel` = CAST(JSON_EXTRACT(`raw_payload`, '$.publicIntel') AS JSON),
    `signals` = CAST(JSON_EXTRACT(`raw_payload`, '$.signals') AS JSON),
    `buyerSignals` = CAST(JSON_EXTRACT(`raw_payload`, '$.buyerSignals') AS JSON),
    `signalGroups` = CAST(JSON_EXTRACT(`raw_payload`, '$.signalGroups') AS JSON),
    `signalNews` = CAST(JSON_EXTRACT(`raw_payload`, '$.signalNews') AS JSON),
    `voiceFeed` = CAST(JSON_EXTRACT(`raw_payload`, '$.voiceFeed') AS JSON),
    `managementCards` = CAST(JSON_EXTRACT(`raw_payload`, '$.managementCards') AS JSON),
    `protections` = CAST(JSON_EXTRACT(`raw_payload`, '$.protections') AS JSON),
    `sentProtections` = CAST(JSON_EXTRACT(`raw_payload`, '$.sentProtections') AS JSON),
    `buyerAlerts` = CAST(JSON_EXTRACT(`raw_payload`, '$.buyerAlerts') AS JSON),
    `buyerLifecycle` = CAST(JSON_EXTRACT(`raw_payload`, '$.buyerLifecycle') AS JSON),
    `buyerExpiryCloses` = CAST(JSON_EXTRACT(`raw_payload`, '$.buyerExpiryCloses') AS JSON),
    `midtermShadowCards` = CAST(JSON_EXTRACT(`raw_payload`, '$.midtermShadowCards') AS JSON),
    `withdrawnCards` = CAST(JSON_EXTRACT(`raw_payload`, '$.withdrawnCards') AS JSON),
    `orderIntents` = CAST(JSON_EXTRACT(`raw_payload`, '$.orderIntents') AS JSON),
    `settlementCards` = CAST(JSON_EXTRACT(`raw_payload`, '$.settlementCards') AS JSON),
    `history` = CAST(JSON_EXTRACT(`raw_payload`, '$.history') AS JSON),
    `historyFingerprint` = CAST(JSON_EXTRACT(`raw_payload`, '$.historyFingerprint') AS JSON),
    `events` = CAST(JSON_EXTRACT(`raw_payload`, '$.events') AS JSON),
    `performance` = CAST(JSON_EXTRACT(`raw_payload`, '$.performance') AS JSON),
    `scan` = CAST(JSON_EXTRACT(`raw_payload`, '$.scan') AS JSON),
    `source_id` = CAST(JSON_EXTRACT(`raw_payload`, '$.source_id') AS JSON),
    `status` = CAST(JSON_EXTRACT(`raw_payload`, '$.status') AS JSON),
    `sourceHealth` = CAST(JSON_EXTRACT(`raw_payload`, '$.sourceHealth') AS JSON),
    `systemStatus` = CAST(JSON_EXTRACT(`raw_payload`, '$.systemStatus') AS JSON),
    `terminalHealth` = CAST(JSON_EXTRACT(`raw_payload`, '$.terminalHealth') AS JSON);

ALTER TABLE `option_seller_guide`
  CHANGE COLUMN `generated_at` `source_generated_at` datetime DEFAULT NULL COMMENT '接口数据生成时间/导入时间',
  CHANGE COLUMN `payload` `raw_payload` longtext NOT NULL COMMENT '接口完整原始 JSON 响应',
  ADD COLUMN `completedVersion` JSON NULL COMMENT '响应字段：completedVersion',
  ADD COLUMN `completedAt` JSON NULL COMMENT '响应字段：completedAt',
  ADD COLUMN `currentVersion` JSON NULL COMMENT '响应字段：currentVersion',
  ADD COLUMN `pending` JSON NULL COMMENT '响应字段：pending';

UPDATE `option_seller_guide`
SET `completedVersion` = CAST(JSON_EXTRACT(`raw_payload`, '$.completedVersion') AS JSON),
    `completedAt` = CAST(JSON_EXTRACT(`raw_payload`, '$.completedAt') AS JSON),
    `currentVersion` = CAST(JSON_EXTRACT(`raw_payload`, '$.currentVersion') AS JSON),
    `pending` = CAST(JSON_EXTRACT(`raw_payload`, '$.pending') AS JSON);

ALTER TABLE `option_entitlement`
  CHANGE COLUMN `generated_at` `source_generated_at` datetime DEFAULT NULL COMMENT '接口数据生成时间/导入时间',
  CHANGE COLUMN `payload` `raw_payload` longtext NOT NULL COMMENT '接口完整原始 JSON 响应',
  ADD COLUMN `access` JSON NULL COMMENT '响应字段：access';

UPDATE `option_entitlement`
SET `access` = CAST(JSON_EXTRACT(`raw_payload`, '$.access') AS JSON);

ALTER TABLE `option_decisions`
  CHANGE COLUMN `generated_at` `source_generated_at` datetime DEFAULT NULL COMMENT '接口数据生成时间/导入时间',
  CHANGE COLUMN `payload` `raw_payload` longtext NOT NULL COMMENT '接口完整原始 JSON 响应',
  ADD COLUMN `latest` JSON NULL COMMENT '响应字段：latest',
  ADD COLUMN `events` JSON NULL COMMENT '响应字段：events';

UPDATE `option_decisions`
SET `latest` = CAST(JSON_EXTRACT(`raw_payload`, '$.latest') AS JSON),
    `events` = CAST(JSON_EXTRACT(`raw_payload`, '$.events') AS JSON);

ALTER TABLE `option_management_choices`
  CHANGE COLUMN `generated_at` `source_generated_at` datetime DEFAULT NULL COMMENT '接口数据生成时间/导入时间',
  CHANGE COLUMN `payload` `raw_payload` longtext NOT NULL COMMENT '接口完整原始 JSON 响应',
  ADD COLUMN `latest` JSON NULL COMMENT '响应字段：latest',
  ADD COLUMN `events` JSON NULL COMMENT '响应字段：events';

UPDATE `option_management_choices`
SET `latest` = CAST(JSON_EXTRACT(`raw_payload`, '$.latest') AS JSON),
    `events` = CAST(JSON_EXTRACT(`raw_payload`, '$.events') AS JSON);

ALTER TABLE `option_premarket`
  CHANGE COLUMN `generated_at` `source_generated_at` datetime DEFAULT NULL COMMENT '接口数据生成时间/导入时间',
  CHANGE COLUMN `payload` `raw_payload` longtext NOT NULL COMMENT '接口完整原始 JSON 响应',
  ADD COLUMN `access` JSON NULL COMMENT '响应字段：access',
  ADD COLUMN `brief` JSON NULL COMMENT '响应字段：brief',
  ADD COLUMN `latest` JSON NULL COMMENT '响应字段：latest',
  ADD COLUMN `receivedAt` JSON NULL COMMENT '响应字段：receivedAt';

UPDATE `option_premarket`
SET `access` = CAST(JSON_EXTRACT(`raw_payload`, '$.access') AS JSON),
    `brief` = CAST(JSON_EXTRACT(`raw_payload`, '$.brief') AS JSON),
    `latest` = CAST(JSON_EXTRACT(`raw_payload`, '$.latest') AS JSON),
    `receivedAt` = CAST(JSON_EXTRACT(`raw_payload`, '$.receivedAt') AS JSON);

ALTER TABLE `option_updates`
  CHANGE COLUMN `generated_at` `source_generated_at` datetime DEFAULT NULL COMMENT '接口数据生成时间/导入时间',
  CHANGE COLUMN `payload` `raw_payload` longtext NOT NULL COMMENT '接口完整原始 JSON 响应',
  ADD COLUMN `seenSeq` JSON NULL COMMENT '响应字段：seenSeq',
  ADD COLUMN `currentSeq` JSON NULL COMMENT '响应字段：currentSeq',
  ADD COLUMN `seenAt` JSON NULL COMMENT '响应字段：seenAt',
  ADD COLUMN `unseen` JSON NULL COMMENT '响应字段：unseen';

UPDATE `option_updates`
SET `seenSeq` = CAST(JSON_EXTRACT(`raw_payload`, '$.seenSeq') AS JSON),
    `currentSeq` = CAST(JSON_EXTRACT(`raw_payload`, '$.currentSeq') AS JSON),
    `seenAt` = CAST(JSON_EXTRACT(`raw_payload`, '$.seenAt') AS JSON),
    `unseen` = CAST(JSON_EXTRACT(`raw_payload`, '$.unseen') AS JSON);

ALTER TABLE `option_support`
  CHANGE COLUMN `generated_at` `source_generated_at` datetime DEFAULT NULL COMMENT '接口数据生成时间/导入时间',
  CHANGE COLUMN `payload` `raw_payload` longtext NOT NULL COMMENT '接口完整原始 JSON 响应',
  ADD COLUMN `mode` JSON NULL COMMENT '响应字段：mode',
  ADD COLUMN `messages` JSON NULL COMMENT '响应字段：messages',
  ADD COLUMN `thread` JSON NULL COMMENT '响应字段：thread',
  ADD COLUMN `unread` JSON NULL COMMENT '响应字段：unread';

UPDATE `option_support`
SET `mode` = CAST(JSON_EXTRACT(`raw_payload`, '$.mode') AS JSON),
    `messages` = CAST(JSON_EXTRACT(`raw_payload`, '$.messages') AS JSON),
    `thread` = CAST(JSON_EXTRACT(`raw_payload`, '$.thread') AS JSON),
    `unread` = CAST(JSON_EXTRACT(`raw_payload`, '$.unread') AS JSON);
