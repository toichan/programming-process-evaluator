package entity;

import java.util.List;

public final class StudentNotificationPage {
	public static final int PAGE_SIZE = 20;

	private final List<StudentNotification> notifications;
	private final int unreadCount;
	private final int pageNumber;
	private final boolean hasNext;

	public StudentNotificationPage(
			List<StudentNotification> notifications,
			int unreadCount,
			int pageNumber,
			boolean hasNext) {
		if (pageNumber < 1 || unreadCount < 0) {
			throw new IllegalArgumentException("The notification page is invalid.");
		}
		this.notifications = List.copyOf(notifications);
		this.unreadCount = unreadCount;
		this.pageNumber = pageNumber;
		this.hasNext = hasNext;
	}

	public List<StudentNotification> getNotifications() {
		return notifications;
	}

	public int getUnreadCount() {
		return unreadCount;
	}

	public int getPageNumber() {
		return pageNumber;
	}

	public boolean isHasNext() {
		return hasNext;
	}

	public boolean getHasNext() {
		return hasNext;
	}

	public boolean isHasPrevious() {
		return pageNumber > 1;
	}
}
