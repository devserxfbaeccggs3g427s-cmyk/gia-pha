package com.familya.search.application.port.in;

public record SearchMembersCommand() {
    public record MemberFilter(Integer birthYear, Integer birthYearFrom, Integer birthYearTo, Boolean tombstoned) { }
}
