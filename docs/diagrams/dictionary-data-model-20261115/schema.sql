-- Schema only: summer_next4_dev, 2026-11-15; no business data

CREATE TABLE `summer_dictionary_type` (
  `id` varchar(36) NOT NULL,
  `code` varchar(80) NOT NULL,
  `name` varchar(200) NOT NULL,
  `published` tinyint(1) NOT NULL DEFAULT '0',
  `public_readable` tinyint(1) NOT NULL DEFAULT '0',
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `tenant_id` varchar(80) NOT NULL DEFAULT 'ent-acme',
  `organization_id` varchar(64) DEFAULT NULL,
  `created_by` varchar(128) DEFAULT NULL,
  `owner_id` varchar(128) DEFAULT NULL,
  `published_version` bigint DEFAULT NULL,
  `draft_revision` bigint NOT NULL DEFAULT '0',
  `effective_revision` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_summer_dictionary_tenant_code` (`tenant_id`,`code`),
  KEY `ix_summer_dictionary_type_scope` (`tenant_id`,`organization_id`,`created_by`,`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `summer_dictionary_item` (
  `id` varchar(36) NOT NULL,
  `dictionary_code` varchar(80) NOT NULL,
  `item_value` varchar(80) NOT NULL,
  `label` varchar(200) NOT NULL,
  `sort_order` int NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `tenant_id` varchar(80) NOT NULL DEFAULT 'ent-acme',
  `organization_id` varchar(64) DEFAULT NULL,
  `created_by` varchar(128) DEFAULT NULL,
  `owner_id` varchar(128) DEFAULT NULL,
  `description` varchar(2000) NOT NULL DEFAULT '',
  `ever_published` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_summer_dictionary_tenant_value` (`tenant_id`,`dictionary_code`,`item_value`),
  UNIQUE KEY `uk_dictionary_item_identity` (`tenant_id`,`dictionary_code`,`id`),
  KEY `ix_summer_dictionary_item_order` (`dictionary_code`,`enabled`,`sort_order`,`item_value`),
  CONSTRAINT `fk_summer_dictionary_tenant_type` FOREIGN KEY (`tenant_id`, `dictionary_code`) REFERENCES `summer_dictionary_type` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `summer_dictionary_group` (
  `id` varchar(36) NOT NULL,
  `tenant_id` varchar(80) NOT NULL,
  `dictionary_code` varchar(80) NOT NULL,
  `code` varchar(80) NOT NULL,
  `name` varchar(200) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `ever_published` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dictionary_group_code` (`tenant_id`,`dictionary_code`,`code`),
  UNIQUE KEY `uk_dictionary_group_identity` (`tenant_id`,`dictionary_code`,`id`),
  CONSTRAINT `fk_dictionary_group_type` FOREIGN KEY (`tenant_id`, `dictionary_code`) REFERENCES `summer_dictionary_type` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `summer_dictionary_group_member` (
  `id` varchar(36) NOT NULL,
  `tenant_id` varchar(80) NOT NULL,
  `dictionary_code` varchar(80) NOT NULL,
  `group_id` varchar(36) NOT NULL,
  `item_id` varchar(36) NOT NULL,
  `sort_order` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dictionary_group_member` (`tenant_id`,`dictionary_code`,`group_id`,`item_id`),
  KEY `fk_dictionary_member_item` (`tenant_id`,`dictionary_code`,`item_id`),
  CONSTRAINT `fk_dictionary_member_group` FOREIGN KEY (`tenant_id`, `dictionary_code`, `group_id`) REFERENCES `summer_dictionary_group` (`tenant_id`, `dictionary_code`, `id`) ON DELETE CASCADE,
  CONSTRAINT `fk_dictionary_member_item` FOREIGN KEY (`tenant_id`, `dictionary_code`, `item_id`) REFERENCES `summer_dictionary_item` (`tenant_id`, `dictionary_code`, `id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `summer_dictionary_version` (
  `version_id` varchar(36) NOT NULL,
  `tenant_id` varchar(80) NOT NULL,
  `dictionary_code` varchar(80) NOT NULL,
  `version_number` bigint NOT NULL,
  `status` varchar(20) NOT NULL,
  `snapshot_json` longtext NOT NULL,
  `created_by` varchar(128) DEFAULT NULL,
  `request_id` varchar(128) DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_summer_dictionary_version_number` (`tenant_id`,`dictionary_code`,`version_number`),
  KEY `ix_summer_dictionary_version_lookup` (`tenant_id`,`dictionary_code`,`version_number`),
  CONSTRAINT `ck_summer_dictionary_version_status` CHECK ((`status` = _utf8mb4'PUBLISHED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `summer_dictionary_outbox` (
  `event_id` varchar(36) NOT NULL,
  `tenant_id` varchar(80) NOT NULL,
  `dictionary_code` varchar(80) NOT NULL,
  `event_type` varchar(80) NOT NULL,
  `version_number` bigint NOT NULL,
  `request_id` varchar(128) DEFAULT NULL,
  `payload_json` text NOT NULL,
  `idempotency_key` varchar(255) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `created_at` timestamp(6) NOT NULL,
  `request_fingerprint` varchar(64) DEFAULT NULL,
  `result_json` longtext,
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_summer_dictionary_outbox_idempotency` (`tenant_id`,`dictionary_code`,`idempotency_key`),
  KEY `ix_summer_dictionary_outbox_lookup` (`tenant_id`,`dictionary_code`,`created_at`),
  CONSTRAINT `ck_summer_dictionary_outbox_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'PUBLISHED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `summer_dictionary_notification_event` (
  `event_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` varchar(80) NOT NULL,
  `dictionary_code` varchar(80) NOT NULL,
  `event_type` varchar(80) NOT NULL,
  `version_number` bigint NOT NULL,
  `request_id` varchar(128) DEFAULT NULL,
  `payload_json` text NOT NULL,
  `created_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`event_id`),
  KEY `ix_summer_dictionary_notification_lookup` (`tenant_id`,`dictionary_code`,`event_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `summer_configuration_override` (
  `id` varchar(36) NOT NULL,
  `tenant_id` varchar(80) NOT NULL,
  `resource` varchar(160) NOT NULL,
  `config_key` varchar(160) NOT NULL,
  `scope_id` varchar(64) NOT NULL,
  `organization_id` varchar(64) DEFAULT NULL,
  `created_by` varchar(128) NOT NULL,
  `owner_id` varchar(128) NOT NULL,
  `value_json` text NOT NULL,
  `public_readable` tinyint(1) NOT NULL,
  `enabled` tinyint(1) NOT NULL,
  `version` bigint NOT NULL,
  `updated_at` timestamp(6) NOT NULL,
  `latest_revision` bigint NOT NULL DEFAULT '0',
  `published_revision` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configuration_override` (`tenant_id`,`resource`,`config_key`,`scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `summer_configuration_override_revision` (
  `id` varchar(36) NOT NULL,
  `override_id` varchar(36) NOT NULL,
  `tenant_id` varchar(80) NOT NULL,
  `revision` bigint NOT NULL,
  `state` varchar(20) NOT NULL,
  `value_json` text NOT NULL,
  `public_readable` tinyint(1) NOT NULL,
  `enabled` tinyint(1) NOT NULL,
  `principal` varchar(128) NOT NULL,
  `request_id` varchar(128) DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_configuration_override_revision` (`override_id`,`revision`),
  CONSTRAINT `fk_configuration_override_revision` FOREIGN KEY (`override_id`) REFERENCES `summer_configuration_override` (`id`),
  CONSTRAINT `ck_configuration_override_revision_state` CHECK ((`state` in (_utf8mb4'DRAFT',_utf8mb4'PUBLISHED',_utf8mb4'DISABLED',_utf8mb4'ROLLED_BACK')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
