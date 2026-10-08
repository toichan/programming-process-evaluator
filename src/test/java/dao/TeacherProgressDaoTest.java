package dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TeacherProgressDaoTest {
	@Test
	void formatsElapsedTimeWithoutLosingHoursOrNegativeValues() {
		assertEquals("00:00:00", TeacherProgressDao.formatDuration(0));
		assertEquals("27:05:09", TeacherProgressDao.formatDuration(27 * 3600 + 5 * 60 + 9));
		assertEquals("00:00:00", TeacherProgressDao.formatDuration(-1));
	}

	@Test
	void mapsDifficultyAndLatestConsentStatesToPrototypeLabels() {
		assertEquals("初級", TeacherProgressDao.difficultyLabel("beginner"));
		assertEquals("中級", TeacherProgressDao.difficultyLabel("intermediate"));
		assertEquals("上級", TeacherProgressDao.difficultyLabel("advanced"));
		assertEquals("なし", TeacherProgressDao.difficultyLabel("none"));
		assertEquals("未設定", TeacherProgressDao.difficultyLabel("unknown"));

		assertEquals("同意", TeacherProgressDao.consentLabel("agreed"));
		assertEquals("不同意", TeacherProgressDao.consentLabel("declined"));
		assertEquals("不同意", TeacherProgressDao.consentLabel("withdrawn"));
		assertEquals("未確認", TeacherProgressDao.consentLabel("unconfirmed"));
		assertEquals("未確認", TeacherProgressDao.consentLabel(null));
	}
}
