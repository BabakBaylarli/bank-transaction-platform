package com.bankplatform.transaction.account;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IbanGeneratorTest {

    private final IbanGenerator generator = new IbanGenerator();

    @RepeatedTest(20)
    void generatesValidAzerbaijaniIbans() {
        String iban = generator.generate();

        assertThat(iban).hasSize(28).startsWith("AZ").contains(IbanGenerator.BANK_CODE);
        assertThat(IbanGenerator.isValid(iban)).isTrue();
    }

    @Test
    void validatesKnownRealWorldIban() {
        assertThat(IbanGenerator.isValid("AZ21NABZ00000000137010001944")).isTrue();
        assertThat(IbanGenerator.isValid("AZ22NABZ00000000137010001944")).isFalse();
    }

    @Test
    void buildIsDeterministic() {
        assertThat(IbanGenerator.build("00000000000000000001"))
                .isEqualTo(IbanGenerator.build("00000000000000000001"))
                .satisfies(iban -> assertThat(IbanGenerator.isValid(iban)).isTrue());
    }
}
