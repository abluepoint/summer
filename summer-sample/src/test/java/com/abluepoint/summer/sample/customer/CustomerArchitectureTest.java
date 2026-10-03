package com.abluepoint.summer.sample.customer;

import com.abluepoint.summer.data.jpa.template.JpaSqlTemplate;
import com.abluepoint.summer.mvc.validate.FieldValues;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerArchitectureTest {

    @Test
    void customerServiceDoesNotDependOnQueryInfrastructureOrMetrics() {
        Stream<Class<?>> fieldTypes = Arrays.stream(CustomerService.class.getDeclaredFields())
                .map(Field::getType);
        Stream<Class<?>> constructorParameterTypes = Arrays.stream(CustomerService.class.getDeclaredConstructors())
                .map(Constructor::getParameterTypes)
                .flatMap(Arrays::stream);

        assertThat(Stream.concat(fieldTypes, constructorParameterTypes))
                .noneMatch(type -> type == JpaSqlTemplate.class
                        || type == Counter.class
                        || type == MeterRegistry.class
                        || type == FieldValues.class);
        assertThat(Arrays.stream(CustomerService.class.getDeclaredFields()).map(Field::getName))
                .noneMatch(name -> name.equals("SQL_RESOURCE") || name.equals("queryCounter"));
    }

    @Test
    void customerServiceOwnsReadAndWriteTransactionBoundaries() throws Exception {
        assertThat(CustomerService.class.getDeclaredMethod("findByUsername", String.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(CustomerService.class.getDeclaredMethod("list", CustomerListQuery.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(CustomerService.class.getDeclaredMethod("create", String.class, String.class)
                .getAnnotation(Transactional.class).readOnly()).isFalse();
        assertThat(CustomerService.class.getDeclaredMethod(
                "update",
                String.class,
                boolean.class,
                String.class,
                Boolean.class
        )
                .getAnnotation(Transactional.class).readOnly()).isFalse();
        assertThat(CustomerService.class.getDeclaredMethod("delete", String.class)
                .getAnnotation(Transactional.class).readOnly()).isFalse();
        assertThat(Arrays.stream(CustomerController.class.getDeclaredMethods()))
                .noneMatch(method -> method.isAnnotationPresent(Transactional.class));
    }

    @Test
    void customerQueryIsTheOnlyCustomerComponentThatOwnsJpaSqlTemplate() {
        assertThat(Arrays.stream(CustomerQuery.class.getDeclaredFields()).map(Field::getType))
                .contains(JpaSqlTemplate.class);
        assertThat(Stream.of(CustomerService.class, CustomerController.class)
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .map(Field::getType))
                .doesNotContain(JpaSqlTemplate.class);
    }

    @Test
    void repositoryStaysASimpleEntityReaderWithoutMvcOrQueryInfrastructure() {
        Stream<Class<?>> fieldTypes = Arrays.stream(CustomerRepository.class.getDeclaredFields())
                .map(Field::getType);
        Stream<Class<?>> methodTypes = Arrays.stream(CustomerRepository.class.getDeclaredMethods())
                .flatMap(method -> Stream.concat(Stream.of(method.getReturnType()), Arrays.stream(method.getParameterTypes())));

        assertThat(Stream.concat(fieldTypes, methodTypes))
                .noneMatch(type -> type == JpaSqlTemplate.class || type == FieldValues.class || type == CustomerPage.class);
    }
}
