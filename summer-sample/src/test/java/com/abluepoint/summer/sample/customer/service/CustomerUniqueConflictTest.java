package com.abluepoint.summer.sample.customer.service;

import com.abluepoint.summer.sample.customer.model.command.CustomerCreateCommand;
import com.abluepoint.summer.sample.customer.model.entity.CustomerEntity;
import com.abluepoint.summer.sample.customer.repository.CustomerRepository;

import com.abluepoint.summer.common.error.SummerException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerUniqueConflictTest {

    @Test
    void namedDatabaseConstraintsBecomeFieldConflictsWithoutSqlDetails() {
        assertConflict("uk_summer_customer_username", "username");
        assertConflict("uk_summer_customer_email", "email");
    }

    @Test
    void unrelatedIntegrityFailuresAreNotMisreportedAsUniqueConflicts() {
        DataIntegrityViolationException failure = databaseFailure("summer_customer_email_not_null");
        assertThat(CustomerService.uniqueConflict(failure)).isSameAs(failure);
    }

    @Test
    void createMapsAConstraintRaceAfterPrechecks() {
        CustomerRepository repository = mock(CustomerRepository.class);
        when(repository.saveAndFlush(any(CustomerEntity.class))).thenThrow(databaseFailure("uk_summer_customer_email"));
        CustomerService service = new CustomerService(repository, mock(CustomerQueryService.class));
        assertThatThrownBy(() -> service.create(new CustomerCreateCommand("race", "race@summer.local", "Race")))
                .isInstanceOfSatisfying(SummerException.class, conflict ->
                        assertThat(conflict.violations()).extracting(violation -> violation.field()).containsExactly("email"));
    }

    private void assertConflict(String constraint, String field) {
        RuntimeException result = CustomerService.uniqueConflict(databaseFailure(constraint));
        assertThat(result).isInstanceOf(SummerException.class);
        SummerException conflict = (SummerException) result;
        assertThat(conflict.errorCode().code()).isEqualTo("SUMMER_CONFLICT");
        assertThat(conflict.violations()).extracting(violation -> violation.field()).containsExactly(field);
        assertThat(conflict.getMessage()).doesNotContain("internal table detail");
        assertThat(conflict.violations().getFirst().message()).doesNotContain("internal table detail");
    }

    private DataIntegrityViolationException databaseFailure(String constraint) {
        SQLException sql = new SQLException("internal table detail", "23505");
        return new DataIntegrityViolationException("internal table detail",
                new ConstraintViolationException("internal table detail", sql, constraint));
    }
}
