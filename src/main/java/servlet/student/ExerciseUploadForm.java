package servlet.student;

import java.util.ArrayList;
import java.util.Base64;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import entity.ExerciseUpload;

final class ExerciseUploadForm {
	static final int MAX_BYTES = 4 * 1024 * 1024;

	private ExerciseUploadForm() {}

	static ExerciseUpload read(String json) {
		if (json == null) throw new IllegalArgumentException("アップロードファイルを指定してください。");
		try {
			var array = JsonParser.parseString(json);
			if (!array.isJsonArray() || array.getAsJsonArray().size() > ExerciseUpload.MAX_FILES) {
				throw new IllegalArgumentException("アップロードは1〜100ファイルで指定してください。");
			}
			var files = new ArrayList<ExerciseUpload.File>();
			for (var item : array.getAsJsonArray()) {
				if (!item.isJsonObject()) throw new IllegalArgumentException("ファイル情報が正しくありません。");
				var object = item.getAsJsonObject();
				for (String key : new String[] {"path", "content"}) {
					if (!object.has(key) || !object.get(key).isJsonPrimitive()
							|| !object.get(key).getAsJsonPrimitive().isString()) {
						throw new IllegalArgumentException("ファイル情報が正しくありません。");
					}
				}
				String encoded = object.get("content").getAsString();
				if (encoded.length() > 4 * ((65536 + 2) / 3)) {
					throw new ExerciseForm.TooLargeException();
				}
				byte[] bytes;
				try {
					bytes = Base64.getDecoder().decode(encoded);
				} catch (IllegalArgumentException e) {
					throw new IllegalArgumentException("アップロードファイルのエンコードが正しくありません。", e);
				}
				files.add(new ExerciseUpload.File(object.get("path").getAsString(), ExerciseForm.utf8(bytes)));
			}
			return new ExerciseUpload(files);
		} catch (JsonParseException e) {
			throw new IllegalArgumentException("アップロード情報の形式が正しくありません。", e);
		}
	}
}
