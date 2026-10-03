package control.auth;

public final class PasswordPolicy {
	private PasswordPolicy() {
	}

	public static boolean isValid(char[] password) {
		if (password == null || password.length < 8 || password.length > 32) {
			return false;
		}

		boolean uppercase = false;
		boolean lowercase = false;
		boolean digit = false;
		boolean symbol = false;
		for (char character : password) {
			if (character < 0x21 || character > 0x7e) {
				return false;
			}
			uppercase |= character >= 'A' && character <= 'Z';
			lowercase |= character >= 'a' && character <= 'z';
			digit |= character >= '0' && character <= '9';
			symbol |= !(character >= 'A' && character <= 'Z')
					&& !(character >= 'a' && character <= 'z')
					&& !(character >= '0' && character <= '9');
		}

		int categories = (uppercase ? 1 : 0) + (lowercase ? 1 : 0) + (digit ? 1 : 0) + (symbol ? 1 : 0);
		return categories >= 3;
	}
}
