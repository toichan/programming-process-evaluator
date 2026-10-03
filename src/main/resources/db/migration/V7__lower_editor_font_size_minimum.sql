ALTER TABLE `user_editor_preferences`
  DROP CHECK `chk_user_editor_preferences_font_size`,
  ADD CONSTRAINT `chk_user_editor_preferences_font_size`
    CHECK (`font_size_px` BETWEEN 10 AND 24);
