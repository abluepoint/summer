package com.abluepoint.summer.sample.customer;

record CustomerListQuery(
        int page,
        int size,
        Boolean active,
        SortField sort,
        SortDirection direction,
        Projection projection
) {

    static final int DEFAULT_PAGE = 0;
    static final int DEFAULT_SIZE = 20;

    CustomerListQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }

    long offset() {
        return Math.multiplyExact((long) page, size);
    }

    enum SortField {
        USERNAME,
        EMAIL,
        DISPLAY_NAME,
        ACTIVE
    }

    enum SortDirection {
        ASC,
        DESC
    }

    enum Projection {
        SUMMARY,
        DETAIL
    }
}
