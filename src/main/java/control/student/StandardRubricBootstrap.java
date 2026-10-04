package control.student;

import java.nio.file.Files;
import java.nio.file.Path;

import dao.StandardRubricDao;
import lib.mysql.Client;

public final class StandardRubricBootstrap {
	private StandardRubricBootstrap() {}

	public static void main(String[] args) throws Exception {
		if (args.length != 0) throw new IllegalArgumentException("標準版の登録にユーザーIDは指定しません。");
		var rubric = StandardRubricSource.parse(
				Files.readString(Path.of("docs/rubric/思考力・判断力・表現力_ルーブリック_0805.md")),
				Files.readString(Path.of("docs/rubric/主体的に学習に取り組む態度_ルーブリック_0805.md")));
		try {
			boolean created = new StandardRubricDao().register(rubric);
			System.out.println(created ? "標準ルーブリックをDBへ登録しました。" : "登録済み標準版の内容一致を確認しました。変更はありません。");
		} finally {
			Client.closeDataSource();
		}
	}
}
