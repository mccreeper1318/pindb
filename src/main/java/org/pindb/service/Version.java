package org.pindb.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class Version implements Comparable<Version> {
    private final String original;
    private final List<Integer> numbers;
    private final List<String> prerelease;

    private Version(String original, List<Integer> numbers, List<String> prerelease) {
        this.original = original;
        this.numbers = List.copyOf(numbers);
        this.prerelease = List.copyOf(prerelease);
    }

    public static Version parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Version cannot be blank.");
        }
        String normalized = text.trim();
        if (normalized.toLowerCase(Locale.ROOT).startsWith("v.")) {
            normalized = normalized.substring(2);
        } else if (normalized.toLowerCase(Locale.ROOT).startsWith("v")) {
            normalized = normalized.substring(1);
        }
        String[] pieces = normalized.split("-", 2);
        String[] numberParts = pieces[0].split("\\.");
        if (numberParts.length < 2) {
            throw new IllegalArgumentException("Version must contain at least two numeric parts: " + text);
        }
        List<Integer> numbers = new ArrayList<>();
        for (String part : numberParts) {
            if (!part.matches("\\d+")) {
                throw new IllegalArgumentException("Invalid numeric version: " + text);
            }
            numbers.add(Integer.parseInt(part));
        }
        List<String> prerelease = pieces.length == 2
                ? List.of(pieces[1].split("\\."))
                : List.of();
        if (prerelease.stream().anyMatch(value -> value.isBlank() || !value.matches("[0-9A-Za-z-]+"))) {
            throw new IllegalArgumentException("Invalid pre-release version: " + text);
        }
        return new Version(text.trim(), numbers, prerelease);
    }

    public String normalized() {
        String numeric = numbers.stream().map(String::valueOf).reduce((a, b) -> a + "." + b).orElse("0.0");
        return prerelease.isEmpty() ? numeric : numeric + "-" + String.join(".", prerelease);
    }

    public boolean isPrerelease() {
        return !prerelease.isEmpty();
    }

    @Override
    public int compareTo(Version other) {
        int max = Math.max(numbers.size(), other.numbers.size());
        for (int index = 0; index < max; index++) {
            int left = index < numbers.size() ? numbers.get(index) : 0;
            int right = index < other.numbers.size() ? other.numbers.get(index) : 0;
            int comparison = Integer.compare(left, right);
            if (comparison != 0) {
                return comparison;
            }
        }
        if (prerelease.isEmpty() && other.prerelease.isEmpty()) {
            return 0;
        }
        if (prerelease.isEmpty()) {
            return 1;
        }
        if (other.prerelease.isEmpty()) {
            return -1;
        }
        int prereleaseMax = Math.max(prerelease.size(), other.prerelease.size());
        for (int index = 0; index < prereleaseMax; index++) {
            if (index >= prerelease.size()) {
                return -1;
            }
            if (index >= other.prerelease.size()) {
                return 1;
            }
            String left = prerelease.get(index);
            String right = other.prerelease.get(index);
            boolean leftNumeric = left.matches("\\d+");
            boolean rightNumeric = right.matches("\\d+");
            int comparison;
            if (leftNumeric && rightNumeric) {
                comparison = Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
            } else if (leftNumeric) {
                comparison = -1;
            } else if (rightNumeric) {
                comparison = 1;
            } else {
                comparison = left.compareToIgnoreCase(right);
            }
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof Version version && compareTo(version) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(numbers, prerelease);
    }

    @Override
    public String toString() {
        return original;
    }
}
