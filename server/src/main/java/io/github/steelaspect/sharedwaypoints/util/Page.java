package io.github.steelaspect.sharedwaypoints.util;

import java.util.List;

/**
 * One page of a list.
 *
 * @param items  the entries on this page
 * @param number 1-based page number (clamped into range)
 * @param count  total number of pages (at least 1)
 */
public record Page<T>(List<T> items, int number, int count) {
	public static <T> Page<T> of(List<T> all, int requestedPage, int pageSize) {
		int count = Math.max(1, (all.size() + pageSize - 1) / pageSize);
		int number = Math.clamp(requestedPage, 1, count);
		int from = (number - 1) * pageSize;
		return new Page<>(all.subList(Math.min(from, all.size()), Math.min(from + pageSize, all.size())), number, count);
	}

	public boolean hasPrevious() {
		return number > 1;
	}

	public boolean hasNext() {
		return number < count;
	}
}
