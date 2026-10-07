package control.evaluation;

import java.util.Map;

public final class GeminiModelCatalog {
	public static final String DEFAULT_MODEL = "gemini-3.1-pro-preview";
	private static final Map<String, String> SELECTABLE_MODELS = Map.of(
			DEFAULT_MODEL, "Gemini 3.1 Pro（Preview）",
			"gemini-3.8-flash", "Gemini 3.8 Flash",
			"gemini-3.7-flash", "Gemini 3.7 Flash");

	private GeminiModelCatalog() {
	}

	public static Map<String, String> selectableModels() {
		return SELECTABLE_MODELS;
	}

	public static boolean isSelectable(String modelId) {
		return modelId != null && SELECTABLE_MODELS.containsKey(modelId);
	}

	public static void requireSelectable(String modelId) {
		if (!isSelectable(modelId)) {
			throw new IllegalArgumentException(
					"このAIモデルでは新規保存・教師AI生成を行えません。選択可能なAIモデルへ変更して下書きを保存してください。"
							+ "適用済みの版は新しい下書きを作成してください。");
		}
	}
}
