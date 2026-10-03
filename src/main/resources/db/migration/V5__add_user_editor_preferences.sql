CREATE TABLE `user_editor_preferences` (
  `user_id` BIGINT NOT NULL,
  `font_size_px` TINYINT UNSIGNED NOT NULL DEFAULT 16,
  `line_wrapping` BOOLEAN NOT NULL DEFAULT FALSE,
  `indent_width` TINYINT UNSIGNED NOT NULL DEFAULT 4,
  `editor_theme` ENUM('dark','light','high_contrast') NOT NULL DEFAULT 'dark',
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_user_editor_preferences_user`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_user_editor_preferences_font_size`
    CHECK (`font_size_px` BETWEEN 14 AND 24),
  CONSTRAINT `chk_user_editor_preferences_indent_width`
    CHECK (`indent_width` IN (2, 4))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
