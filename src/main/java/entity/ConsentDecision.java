package entity;

public final class ConsentDecision {
	private ConsentDecision() {
	}

	public static ConsentStatus resolve(ConsentStatus current, ConsentStatus selected) {
		if (selected != ConsentStatus.AGREED && selected != ConsentStatus.DECLINED) {
			throw new IllegalArgumentException("A consent decision must be agreed or declined.");
		}
		if (selected == ConsentStatus.DECLINED
				&& (current == ConsentStatus.AGREED || current == ConsentStatus.WITHDRAWN)) {
			return ConsentStatus.WITHDRAWN;
		}
		return selected;
	}
}
