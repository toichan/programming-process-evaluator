package control.auth;

import java.util.ArrayList;
import java.util.List;

public final class PasswordPolicy {
	private PasswordPolicy() {
	}

	public static boolean isValid(char[] password) {
		return violations(password).isEmpty();
	}

	public static List<String> violations(char[] password) {
		List<String> errors = new ArrayList<>();
		if (password == null || password.length < 8 || password.length > 32) {
			errors.add("新しいパスワードは8文字以上32文字以下で入力してください。");
		}

		boolean uppercase = false;
		boolean lowercase = false;
		boolean digit = false;
		boolean symbol = false;
		boolean allowedCharacters = true;
		for (char character : password == null ? new char[0] : password) {
			if (character < 0x21 || character > 0x7e) {
				allowedCharacters = false;
				continue;
			}
			uppercase |= character >= 'A' && character <= 'Z';
			lowercase |= character >= 'a' && character <= 'z';
			digit |= character >= '0' && character <= '9';
			symbol |= !(character >= 'A' && character <= 'Z')
					&& !(character >= 'a' && character <= 'z')
					&& !(character >= '0' && character <= '9');
		}

		int categories = (uppercase ? 1 : 0) + (lowercase ? 1 : 0) + (digit ? 1 : 0) + (symbol ? 1 : 0);
		if (!allowedCharacters) {
			errors.add("半角英数字・記号のみ使用できます。空白・全角文字・制御文字は使用できません。");
		}
		if (categories < 3) {
			errors.add("英大文字・英小文字・数字・記号のうち3種類以上を含めてください（現在" + categories + "種類）。");
		}
		return List.copyOf(errors);
	}
}
